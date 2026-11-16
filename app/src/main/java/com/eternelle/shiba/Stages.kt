package com.eternelle.shiba

import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private class ArRefs {
    var session: Session? = null
    var frame: Frame? = null
}

/** Live AR: plane detection, tap/centre hit-testing, an anchor that faces her, and the universe on it. */
@Composable
fun ArStage(story: Story, onFail: (String) -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val refs = remember { ArRefs() }
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var planeFound by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun place(x: Float, y: Float): Boolean {
        if (anchor != null) return true
        val frame = refs.frame ?: return false
        val session = refs.session ?: return false
        val hit = try {
            frame.hitTest(x, y).firstOrNull { h ->
                val t = h.trackable
                t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING && t.isPoseInPolygon(h.hitPose)
            }
        } catch (_: Exception) { null } ?: return false
        val hp = hit.hitPose
        val cam = frame.camera.pose
        // rotate the universe so its front (+Z) faces her
        val yaw = atan2(cam.tx() - hp.tx(), cam.tz() - hp.tz())
        val pose = Pose(floatArrayOf(hp.tx(), hp.ty(), hp.tz()), floatArrayOf(0f, sin(yaw / 2f), 0f, cos(yaw / 2f)))
        anchor = try { session.createAnchor(pose) } catch (_: Exception) { null }
        if (anchor != null) {
            story.placed = true
            story.sfx(Sfx.CHIME)
        }
        return anchor != null
    }

    DisposableEffect(Unit) {
        story.placeCenter = { if (size.width > 0) place(size.width / 2f, size.height / 2f) else false }
        onDispose {
            story.placeCenter = null
            anchor?.detach()
        }
    }
    LaunchedEffect(planeFound, failure, anchor) {
        story.arHint = when {
            anchor != null -> ""
            failure != null && failure != TrackingFailureReason.NONE -> when (failure) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "It's a little dark — try a brighter spot."
                TrackingFailureReason.EXCESSIVE_MOTION -> "Slowly… move your phone a bit more gently."
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a floor or table with some texture."
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "The camera is busy — close other camera apps."
                else -> "Hold on… finding your world."
            }
            planeFound -> "Found a surface! Tap a sparkly spot to place your star."
            else -> "Slowly move your phone and point it at the floor or a table."
        }
        story.planeFound = planeFound
    }

    ARSceneView(
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
        engine = engine,
        modelLoader = modelLoader,
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
        planeRenderer = anchor == null,
        onSessionFailed = { e -> onFail(e.message ?: "AR could not start on this phone.") },
        onSessionUpdated = { session, frame ->
            refs.session = session
            refs.frame = frame
            if (!planeFound && frame.getUpdatedPlanes().any {
                    it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
                }) planeFound = true
        },
        onTrackingFailureChanged = { failure = it },
        onTouchEvent = { e, _ ->
            if (anchor == null && e.action == MotionEvent.ACTION_UP) place(e.x, e.y)
            false
        },
    ) {
        anchor?.let { a ->
            AnchorNode(anchor = a) {
                Node(scale = Scale(story.arScale)) { Universe(story) }
            }
        }
    }
}

/** Non-AR mode for every other phone: the same universe on a starry backdrop, drag to orbit. */
@Composable
fun FallbackStage(story: Story) {
    val manipulator = remember {
        createDefaultCameraManipulator(
            orbitHomePosition = Position(0f, 0.42f, 1.0f),
            targetPosition = Position(0f, 0.2f, 0f),
        )
    }
    LaunchedEffect(Unit) { story.placed = true }
    SceneView(
        modifier = Modifier.fillMaxSize(),
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        autoCenterContent = false,
        cameraManipulator = manipulator,
    ) {
        Universe(story)
    }
}
