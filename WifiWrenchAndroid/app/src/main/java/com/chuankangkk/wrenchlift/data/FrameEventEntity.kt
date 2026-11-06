package com.chuankangkk.wrenchlift.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通信记录：一条记录（会话）期间收发的非 0x12 帧与关键操作（0x12 逐点在 torque_points）。
 * 只用于导出「通信记录」页排查问题，不参与测量计算。
 */
@Entity(
    tableName = "frame_events",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class FrameEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestampMillis: Long,
    /** 「收」或「发」。 */
    val direction: String,
    val functionCode: Int?,
    val summary: String,
    val rawHex: String,
)
