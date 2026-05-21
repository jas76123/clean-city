package com.example.cleancity.ui.feature.mycomplaints

import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyComplaintsScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun complaint(id: Long) = ComplaintResponse(
        id = id,
        authorId = 1,
        category = ProblemCategory.GARBAGE,
        title = "Жалоба $id",
        description = "описание",
        latitude = 43.5,
        longitude = 39.7,
        address = "адрес",
        status = ComplaintStatus.NEW,
        createdAt = "2026-05-21T09:00:00Z",
        updatedAt = "2026-05-21T09:00:00Z",
    )

    private fun page(ids: LongRange) = ComplaintListResponse(
        items = ids.map { complaint(it) },
        page = 0, size = 20, total = ids.count().toLong(),
    )

    @Test fun `load with items yields Loaded`() = runTest {
        val api = FakeMineComplaintsApi().apply { pages = mapOf(0 to Result.success(page(1L..3L))) }
        val model = MyComplaintsScreenModel(api)

        model.load()

        val state = model.state.value as MyComplaintsState.Loaded
        assertEquals(3, state.complaints.size)
        assertTrue(state.endReached)
    }

    @Test fun `load with empty list yields Empty`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(0 to Result.success(ComplaintListResponse(emptyList(), 0, 20, 0)))
        }
        val model = MyComplaintsScreenModel(api)

        model.load()

        assertEquals(MyComplaintsState.Empty, model.state.value)
    }

    @Test fun `load failure yields Error`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(0 to Result.failure(RuntimeException("нет сети")))
        }
        val model = MyComplaintsScreenModel(api)

        model.load()

        assertTrue(model.state.value is MyComplaintsState.Error)
    }

    @Test fun `full first page is not endReached and loadNextPage appends`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(
                0 to Result.success(page(1L..20L)),
                1 to Result.success(page(21L..25L)),
            )
        }
        val model = MyComplaintsScreenModel(api)
        model.load()
        assertEquals(false, (model.state.value as MyComplaintsState.Loaded).endReached)

        model.loadNextPage()

        val state = model.state.value as MyComplaintsState.Loaded
        assertEquals(25, state.complaints.size)
        assertTrue(state.endReached)
        assertEquals(listOf(0 to 20, 1 to 20), api.mineCalls)
    }
}
