package com.example.mobilewebscraper.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilewebscraper.data.ScrapedData
import com.example.mobilewebscraper.data.VolcanicData
import com.example.mobilewebscraper.service.VolcanoScraperService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VolcanoViewModel : ViewModel() {
    private val scraperService = VolcanoScraperService()
    
    private val _uiState = MutableStateFlow(VolcanoUiState())
    val uiState: StateFlow<VolcanoUiState> = _uiState.asStateFlow()
    
    fun scrapeUrl(url: String, deepScrape: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = scraperService.scrapeVolcanoData(url, deepScrape)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                scrapedData = result,
                error = if (!result.isSuccess) result.errorMessage else null
            )
        }
    }
}

data class VolcanoUiState(
    val isLoading: Boolean = false,
    val scrapedData: ScrapedData? = null,
    val error: String? = null
)