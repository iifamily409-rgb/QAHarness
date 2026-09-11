package com.example.qaharness

import java.io.Serializable

data class DetectedPiece(
    val centerX: Double,
    val centerY: Double,
    val radius: Double
) : Serializable

data class PocketCandidate(
    val centerX: Double,
    val centerY: Double,
    val radius: Double
) : Serializable

data class ShotPlan(
    val aimVectorX: Double,
    val aimVectorY: Double,
    val power: Float,
    val ghostX: Double,
    val ghostY: Double,
    val reflected: Boolean
) : Serializable

data class AnalysisResult(
    val frameBytes: ByteArray,
    val pieces: List<DetectedPiece>,
    val pockets: List<PocketCandidate>,
    val shot: ShotPlan?,
    val logs: List<String>,
    val boardWidth: Int,
    val boardHeight: Int
) : Serializable
