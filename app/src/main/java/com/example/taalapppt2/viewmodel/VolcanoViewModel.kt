package com.example.taalapppt2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taalapppt2.service.ScrapedData
import com.example.taalapppt2.service.fetchVolcanoData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VolcanoViewModel : ViewModel() {

    // MutableStateFlow to hold the scraped data. It's mutable within the ViewModel.
    private val _volcanoData = MutableStateFlow<List<ScrapedData>>(emptyList())

    // StateFlow to expose the data to the UI. It's read-only from the UI's perspective.
    val volcanoData: StateFlow<List<ScrapedData>> = _volcanoData

    // Loading state to inform the UI whether data is being fetched
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Error state to inform the UI if an error occurred during fetching
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Fetch data when the ViewModel is first created
        fetchData()
    }

    fun fetchData() {
        viewModelScope.launch {
            _isLoading.value = true // Set loading to true
            _error.value = null // Clear any previous errors
            try {
                val data = fetchVolcanoData() // Call your service function
                _volcanoData.value = data // Update the StateFlow with fetched data
            } catch (e: Exception) {
                _error.value = "Error fetching data: ${e.message}" // Set error message
                _volcanoData.value = emptyList() // Clear data on error
                e.printStackTrace() // Print stack trace for debugging
            } finally {
                _isLoading.value = false // Set loading to false regardless of success or failure
            }
        }
    }
}