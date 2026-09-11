package com.example.qaharness

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private fun distance(a: Point, b: Point): Double {
    return hypot(a.x - b.x, a.y - b.y)
}

private fun normalize(v: Point): Point {
    val mag = hypot(v.x, v.y)
    return if (mag < 1e-8) Point(0.0, 0.0) else Point(v.x / mag, v.y / mag)
}

private fun orderQuadrilateral(points: Array<Point>): Array<Point> {
    require(points.size == 4) { "Expected four quadrilateral points." }

    val center = Point(
        points.sumOf { it.x } / points.size,
        points.sumOf { it.y } / points.size
    )

    val corners = points.sortedBy { (it.x - center.x) * (it.x - center.x) + (it.y - center.y) * (it.y - center.y) }
    val tl = corners[0]
    val br = corners[3]
    val others = corners.filter { it != tl && it != br }
    val tr = others.maxByOrNull { it.x - it.y } ?: others[0]
    val bl = others.filter { it != tr }.minByOrNull { it.x - it.y } ?: others[1]
    return arrayOf(tl, tr, br, bl)
}

private fun reflectPointAcrossLine(point: Point, linePoint: Point, lineNormal: Point): Point {
    val n = normalize(lineNormal)
    val delta = Point(point.x - linePoint.x, point.y - linePoint.y)
    val dot = delta.x * n.x + delta.y * n.y
    return Point(point.x - 2.0 * dot * n.x, point.y - 2.0 * dot * n.y)
}

class BoardDetector {
    fun detectBoard(bitmap: Bitmap): Mat? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        if (src.empty()) {
            return null
        }

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val largest = contours
            .filter { Imgproc.contourArea(it) > 5000.0 }
            .maxByOrNull { Imgproc.contourArea(it) }
            ?: return null

        val source = MatOfPoint2f(*largest.toArray())
        val perimeter = Imgproc.arcLength(source, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(source, approx, 0.02 * perimeter, true)

        val approxPoints = approx.toArray()
        val pointsToWarp = if (approxPoints.size == 4) {
            orderQuadrilateral(approxPoints)
        } else {
            val rect = Imgproc.boundingRect(largest)
            arrayOf(
                Point(rect.x.toDouble(), rect.y.toDouble()),
                Point((rect.x + rect.width).toDouble(), rect.y.toDouble()),
                Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                Point(rect.x.toDouble(), (rect.y + rect.height).toDouble())
            )
        }

        val tl = pointsToWarp[0]
        val tr = pointsToWarp[1]
        val br = pointsToWarp[2]
        val bl = pointsToWarp[3]

        val widthA = distance(br, bl)
        val widthB = distance(tr, tl)
        val maxWidth = max(widthA, widthB).coerceAtLeast(1.0)

        val heightA = distance(tr, br)
        val heightB = distance(tl, bl)
        val maxHeight = max(heightA, heightB).coerceAtLeast(1.0)

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth - 1.0, 0.0),
            Point(maxWidth - 1.0, maxHeight - 1.0),
            Point(0.0, maxHeight - 1.0)
        )

        val warpMatrix = Imgproc.getPerspectiveTransform(MatOfPoint2f(*pointsToWarp), dst)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, warpMatrix, Size(maxWidth, maxHeight))
        return warped
    }
}

class PieceDetector {
    fun detectPieces(board: Mat): List<DetectedPiece> {
        val gray = Mat()
        Imgproc.cvtColor(board, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray,
            circles,
            Imgproc.HOUGH_GRADIENT,
            dp = 1.2,
            minDist = 14.0,
            param1 = 120.0,
            param2 = 18.0,
            minRadius = 8,
            maxRadius = 40
        )

        val pieces = mutableListOf<DetectedPiece>()
        if (circles.empty()) {
            return pieces
        }

        for (i in 0 until circles.cols()) {
            val circle = circles.get(0, i) ?: continue
            if (circle.size < 3) continue

            val centerX = circle[0]
            val centerY = circle[1]
            val radius = circle[2]

            val center = Point(centerX, centerY)
            if (center.x < radius + 4.0 || center.y < radius + 4.0) continue
            if (center.x > board.cols() - radius - 4.0 || center.y > board.rows() - radius - 4.0) continue

            pieces.add(DetectedPiece(centerX, centerY, radius))
        }

        val unique = mutableListOf<DetectedPiece>()
        for (piece in pieces) {
            var duplicate = false
            for (existing in unique) {
                val dx = piece.centerX - existing.centerX
                val dy = piece.centerY - existing.centerY
                val dist = hypot(dx, dy)
                if (dist < 0.75 * max(piece.radius, existing.radius)) {
                    duplicate = true
                    break
                }
            }
            if (!duplicate) {
                unique.add(piece)
            }
        }

        return unique.sortedBy { it.centerY }
    }
}

class PocketDetector {
    fun detectPockets(board: Mat): List<PocketCandidate> {
        val width = board.cols().toDouble()
        val height = board.rows().toDouble()
        val inset = min(width, height) * 0.08

        return listOf(
            PocketCandidate(inset, inset, 18.0),
            PocketCandidate(width - inset, inset, 18.0),
            PocketCandidate(width - inset, height - inset, 18.0),
            PocketCandidate(inset, height - inset, 18.0)
        )
    }
}

class PhysicsSolver {
    fun solve(
        cue: Point,
        target: Point,
        pocket: Point,
        restitution: Double,
        railNormal: Point? = null,
        railPoint: Point? = null,
        targetRadius: Double = 18.0
    ): ShotPlan {
        require(restitution in 0.0..1.0) { "Coefficient of restitution must be in [0, 1]." }

        val currentPocket = if (railNormal != null && railPoint != null) {
            reflectPointAcrossLine(pocket, railPoint, railNormal)
        } else {
            pocket
        }

        val dirToPocket = normalize(Point(currentPocket.x - target.x, currentPocket.y - target.y))
        val ghost = Point(
            target.x + dirToPocket.x * (targetRadius + 2.0),
            target.y + dirToPocket.y * (targetRadius + 2.0)
        )

        val aimRaw = Point(ghost.x - cue.x, ghost.y - cue.y)
        val aimVector = normalize(aimRaw)

        val ghostDist = hypot(aimRaw.x, aimRaw.y)
        val referenceDistance = max(80.0, distance(cue, target) + 100.0)
        val power = ((ghostDist / referenceDistance) * (0.75 + restitution * 0.45)).coerceIn(0.0, 1.0).toFloat()

        return ShotPlan(
            aimVectorX = aimVector.x,
            aimVectorY = aimVector.y,
            power = power,
            ghostX = ghost.x,
            ghostY = ghost.y,
            reflected = railNormal != null && railPoint != null
        )
    }
}
