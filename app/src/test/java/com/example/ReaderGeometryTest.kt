package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.source.ChapterModel
import com.example.ui.reader.*
import org.junit.Assert.*
import org.junit.Test

class ReaderGeometryTest {
    @Test fun `pinch keeps the touched image coordinate fixed off center`() {
        val viewport = Size(400f, 800f)
        val focus = Offset(280f, 300f)
        val old = PageTransform(2f, Offset(-30f, 70f))
        val result = transformAround(old, 1.5f, focus, Offset.Zero, viewport, viewport)
        val center = Offset(200f, 400f)
        val imageCoordinateBefore = (focus - center - old.pan) / old.scale
        val imageCoordinateAfter = (focus - center - result.pan) / result.scale
        assertEquals(imageCoordinateBefore.x, imageCoordinateAfter.x, 0.001f)
        assertEquals(imageCoordinateBefore.y, imageCoordinateAfter.y, 0.001f)
    }
    @Test fun `pinch translation tracks both fingers and scale is clamped`() {
        val image = Size(400f, 800f)
        val result = transformAround(PageTransform(2f), 1f, Offset(200f, 400f), Offset(40f, -50f), image, image)
        assertEquals(Offset(40f, -50f), result.pan)
        assertEquals(4.5f, transformAround(result, 10f, Offset.Zero, Offset.Zero, image, image).scale)
        assertEquals(PageTransform(), transformAround(result, 0.01f, Offset.Zero, Offset.Zero, image, image))
    }
    @Test fun `portrait and landscape bounds use the displayed image not the container`() {
        val viewport = Size(400f, 800f)
        val portrait = fittedImageSize(Size(400f, 1600f), viewport)
        assertEquals(Size(200f, 800f), portrait)
        assertEquals(Offset(0f, 400f), panLimits(portrait, viewport, 2f))
        val landscape = fittedImageSize(Size(1600f, 400f), viewport)
        assertEquals(Size(400f, 100f), landscape)
        assertEquals(Offset(200f, 0f), panLimits(landscape, viewport, 2f))
    }
    @Test fun `pan cannot expose space beyond image edges`() {
        assertEquals(Offset(-100f, 200f), clampPan(Offset(-999f, 999f), Offset(100f, 200f)))
    }
    @Test fun `webtoon anchoring retains the same point during zoom`() {
        val oldOffset = 300
        val focus = 200f
        val newOffset = anchoredScrollOffset(oldOffset, focus, 1f, 2.5f)
        assertEquals((oldOffset + focus) / 1f, (newOffset + focus) / 2.5f, 0.01f)
        assertEquals(oldOffset, anchoredScrollOffset(newOffset, focus, 2.5f, 1f))
    }
    @Test fun `chapter pull only triggers on release beyond threshold and can be reversed`() {
        val pulled = accumulateChapterPull(0f, -90f, true, true, 72f)
        assertEquals(true, releasedChapterPull(pulled, 72f))
        val reversed = accumulateChapterPull(pulled, 70f, true, true, 72f)
        assertNull(releasedChapterPull(reversed, 72f))
        assertEquals(false, releasedChapterPull(72f, 72f))
        assertEquals(0f, accumulateChapterPull(0f, -100f, true, false, 72f))
        assertEquals(0f, accumulateChapterPull(0f, 100f, false, true, 72f))
    }
    @Test fun `reaching an image edge mid gesture does not turn the page`() {
        assertFalse(canSwipeFromEdge(0f, 200f, -500f))
        assertTrue(canSwipeFromEdge(-200f, 200f, -100f))
        assertFalse(canSwipeFromEdge(-200f, 200f, 100f))
        assertTrue(canSwipeFromEdge(200f, 200f, 100f))
    }
    @Test fun `chapter order works for ascending descending and decimal chapters`() {
        val chapters = listOf(chapter("a", 1f), chapter("b", 1.5f), chapter("c", 2f))
        for (order in listOf(chapters, chapters.reversed())) {
            assertEquals("b", adjacentChapter(order, "a", true)?.id)
            assertEquals("b", adjacentChapter(order, "c", false)?.id)
            assertNull(adjacentChapter(order, "a", false))
            assertNull(adjacentChapter(order, "c", true))
            assertNull(adjacentChapter(order, "missing", true))
        }
    }
    @Test fun `both double tap modes can return to normal`() {
        assertEquals(2.5f, nextDoubleTapScale(1f, false))
        assertEquals(1f, nextDoubleTapScale(2.5f, false))
        assertEquals(1.75f, nextDoubleTapScale(1f, true))
        assertEquals(2.75f, nextDoubleTapScale(1.75f, true))
        assertEquals(4f, nextDoubleTapScale(2.75f, true))
        assertEquals(1f, nextDoubleTapScale(4f, true))
    }
    private fun chapter(id: String, number: Float) = ChapterModel(id, "Capítulo $number", id, number)
}
