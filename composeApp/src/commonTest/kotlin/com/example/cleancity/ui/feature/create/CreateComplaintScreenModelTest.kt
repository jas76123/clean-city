package com.example.cleancity.ui.feature.create

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.DuplicateCandidateResponse
import com.example.cleancity.shared.models.DuplicateCandidatesResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import com.example.cleancity.ui.feature.map.FakeMapSearchProvider
import com.example.cleancity.ui.feature.map.ReverseGeocodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CreateComplaintScreenModelTest {

    private lateinit var api: FakeCreateComplaintsApi
    private lateinit var location: FakeLocationProvider
    private lateinit var search: FakeMapSearchProvider

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeCreateComplaintsApi()
        location = FakeLocationProvider()
        search = FakeMapSearchProvider()
    }

    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun newModel() = CreateComplaintScreenModel(api, location, search)

    private fun photo(name: String = "p.jpg") = PhotoBytes(byteArrayOf(1, 2, 3), name)

    // --- photos -----------------------------------------------------------------

    @Test fun `onPhotosAdded appends up to MAX_PHOTOS, drops excess`() = runTest {
        val model = newModel()
        model.onPhotosAdded(listOf(photo("a"), photo("b"), photo("c")))
        assertEquals(3, model.state.value.photos.size)
        model.onPhotosAdded(listOf(photo("d"), photo("e"), photo("f"), photo("g")))
        // 3 + 4 → trim to MAX_PHOTOS (5), excess dropped
        assertEquals(MAX_PHOTOS, model.state.value.photos.size)
        assertEquals(listOf("a", "b", "c", "d", "e"), model.state.value.photos.map { it.filename })
    }

    @Test fun `onPhotoRemoved removes by index`() = runTest {
        val model = newModel()
        model.onPhotosAdded(listOf(photo("a"), photo("b"), photo("c")))
        model.onPhotoRemoved(1)
        assertEquals(listOf("a", "c"), model.state.value.photos.map { it.filename })
    }

    @Test fun `onPhotoRemoved is no-op for out-of-bounds index`() = runTest {
        val model = newModel()
        model.onPhotosAdded(listOf(photo("a")))
        model.onPhotoRemoved(5)
        assertEquals(1, model.state.value.photos.size)
    }

    // --- description ------------------------------------------------------------

    @Test fun `onDescriptionChanged enforces max length`() = runTest {
        val model = newModel()
        model.onDescriptionChanged("a".repeat(MAX_DESCRIPTION_LENGTH))
        assertEquals(MAX_DESCRIPTION_LENGTH, model.state.value.description.length)
        // attempting to set longer is rejected (state stays at previous value)
        model.onDescriptionChanged("a".repeat(MAX_DESCRIPTION_LENGTH + 5))
        assertEquals(MAX_DESCRIPTION_LENGTH, model.state.value.description.length)
    }

    // --- location flow ----------------------------------------------------------

    @Test fun `onLocationPermissionGranted fills coords and reverse-geocoded address`() = runTest {
        location.nextResult = Result.success(Location(43.585, 39.723))
        search.nextReverseResult = Result.success(
            ReverseGeocodeResult(address = "ул. Транспортная, 14", district = "Центральный"),
        )
        val model = newModel()

        model.onLocationPermissionGranted()

        val s = model.state.value
        assertEquals(43.585, s.latitude)
        assertEquals(39.723, s.longitude)
        assertEquals("ул. Транспортная, 14", s.address)
        assertEquals("Центральный", s.district)
        assertEquals(LocationStatus.Ready, s.locationStatus)
    }

    @Test fun `reverse geocoding does not overwrite user-typed address`() = runTest {
        location.nextResult = Result.success(Location(43.585, 39.723))
        search.nextReverseResult = Result.success(
            ReverseGeocodeResult(address = "ул. Транспортная, 14", district = "Центральный"),
        )
        val model = newModel()

        model.onAddressChanged("ручной адрес")
        model.onLocationPermissionGranted()

        assertEquals("ручной адрес", model.state.value.address)
        // district из геокодера сохраняется
        assertEquals("Центральный", model.state.value.district)
    }

    @Test fun `onLocationPermissionDenied sets PermissionDenied status`() = runTest {
        val model = newModel()
        model.onLocationPermissionDenied()
        assertEquals(LocationStatus.PermissionDenied, model.state.value.locationStatus)
    }

    // --- duplicates -------------------------------------------------------------

    @Test fun `selecting category triggers duplicates fetch when coords set`() = runTest {
        location.nextResult = Result.success(Location(43.5, 39.7))
        search.nextReverseResult = Result.success(ReverseGeocodeResult("ул", null))
        api.nextDuplicatesResult = Result.success(
            DuplicateCandidatesResponse(items = listOf(dupCandidate(1L))),
        )
        val model = newModel()
        model.onLocationPermissionGranted()

        model.onCategorySelected(ProblemCategory.GARBAGE)

        // дождаться debounce + асинхронных update'ов
        testScheduler.advanceUntilIdle()

        val calls = api.duplicateCalls
        assertTrue(calls.isNotEmpty(), "findDuplicates should have been called")
        assertEquals(ProblemCategory.GARBAGE, calls.first().cat)
        assertEquals(1, model.state.value.duplicates.size)
    }

    @Test fun `duplicates not fetched without coords`() = runTest {
        val model = newModel()
        model.onCategorySelected(ProblemCategory.GARBAGE)
        testScheduler.advanceUntilIdle()
        assertEquals(0, api.duplicateCalls.size)
    }

    // --- canSubmit / submit -----------------------------------------------------

    @Test fun `canSubmit false when missing photos`() = runTest {
        val model = readyToSubmitModel(photos = emptyList())
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `canSubmit false when category not selected`() = readyButMissing { it.copy(category = null) }

    @Test fun `canSubmit false when address blank`() = readyButMissing { it.copy(address = "") }

    @Test fun `canSubmit false when coords missing`() = readyButMissing { it.copy(latitude = null) }

    @Test fun `canSubmit true when all required fields set`() = runTest {
        val model = readyToSubmitModel()
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun `submit emits CreatedSuccessfully on success`() = runTest {
        val model = readyToSubmitModel()
        api.nextCreateResult = Result.success(stubComplaint(id = 99))

        val eventDeferred = async { model.events.first() }
        testScheduler.advanceUntilIdle() // подписчик встал в очередь до emit
        model.submit()
        val event = eventDeferred.await()

        assertTrue(event is CreateComplaintEvent.CreatedSuccessfully)
        assertEquals(99L, event.complaintId)
        assertEquals(1, api.createCalls.size)
        assertEquals("ул", api.createCalls.first().request.address)
    }

    @Test fun `submit sets submitError on failure`() = runTest {
        val model = readyToSubmitModel()
        api.nextCreateResult = Result.failure(RuntimeException("net down"))

        model.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("net down", model.state.value.submitError)
        assertFalse(model.state.value.isSubmitting)
    }

    @Test fun `submit no-op when canSubmit is false`() = runTest {
        val model = newModel() // empty state
        model.submit()
        assertEquals(0, api.createCalls.size)
    }

    // --- voteForDuplicate -------------------------------------------------------

    @Test fun `voteForDuplicate emits OpenExistingComplaint on success`() = runTest {
        api.nextVoteResult = Result.success(VoteResponse(votesCount = 5, userVoted = true))
        val model = newModel()

        val eventDeferred = async { model.events.first() }
        testScheduler.advanceUntilIdle()
        model.voteForDuplicate(42L)
        val event = eventDeferred.await()

        assertTrue(event is CreateComplaintEvent.OpenExistingComplaint)
        assertEquals(42L, event.complaintId)
        assertEquals(listOf(42L), api.voteCalls)
    }

    // --- helpers ----------------------------------------------------------------

    private fun readyButMissing(mutator: (CreateComplaintUiState) -> CreateComplaintUiState) = runTest {
        val model = readyToSubmitModel()
        val orig = model.state.value
        val mutated = mutator(orig)
        // Recreate model state via direct calls is heavy; just assert the predicate against the
        // mutated copy. canSubmit is a pure derivation, so this exercises the same logic.
        assertFalse(mutated.canSubmit)
    }

    private fun readyToSubmitModel(
        photos: List<PhotoBytes> = listOf(photo("a")),
    ): CreateComplaintScreenModel {
        location.nextResult = Result.success(Location(43.5, 39.7))
        search.nextReverseResult = Result.success(ReverseGeocodeResult("ул", null))
        val model = newModel()
        model.onPhotosAdded(photos)
        model.onCategorySelected(ProblemCategory.GARBAGE)
        model.onLocationPermissionGranted()
        return model
    }

    private fun stubComplaint(id: Long) = ComplaintResponse(
        id = id,
        authorId = 1L,
        category = ProblemCategory.GARBAGE,
        title = "test",
        description = "",
        latitude = 43.5,
        longitude = 39.7,
        address = "ул",
        status = ComplaintStatus.NEW,
        createdAt = "2026-05-19T10:00:00Z",
        updatedAt = "2026-05-19T10:00:00Z",
    )

    private fun dupCandidate(id: Long) = DuplicateCandidateResponse(
        id = id,
        title = "Свалка",
        category = ProblemCategory.GARBAGE,
        status = ComplaintStatus.NEW,
        address = "ул",
        votesCount = 10,
        distanceMeters = 25,
        createdAt = "2026-05-19T08:00:00Z",
    )
}

