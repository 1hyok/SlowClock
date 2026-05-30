package com.example.slowclock.ui.information


import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slowclock.data.model.InfoData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

@HiltViewModel
class InfoViewModel @Inject constructor() : ViewModel() {
    private val _infoList = mutableStateListOf<InfoData>()
    val infoList = _infoList

    private val _isLoading = mutableStateOf(false)
    val isLoading = _isLoading

    fun fetchInfo() {
        _isLoading.value = true
        viewModelScope.launch{
            try{
                val fetchedInfo = getInfo()
                _infoList.clear()
                _infoList.addAll(fetchedInfo)
            }catch (e: Exception){
                Log.e("Jsoup", "Error in fetch", e)
            } finally {
                _isLoading.value = false  // 이게 반드시 필요함!!
            }
        }
    }
}

private suspend fun getInfo(): List<InfoData> = withContext(Dispatchers.IO){
    val baseUrl = "https://www.medicaltimes.com"
    val doc = Jsoup.connect("$baseUrl/Main/News/List.html?MainCate=6&SubCate=79")
        .userAgent("Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
        .referrer("https://www.google.com")
        .timeout(10000)
        .get()

    val articles = doc.select("article.newsList_cont")
    articles.mapNotNull { article ->
        val title = article.selectFirst("h4.headLine")?.text()?.trim()
        val relativeUrl = article.selectFirst("a")?.attr("href")
        val fullUrl = if (!relativeUrl.isNullOrBlank()) "$baseUrl$relativeUrl" else null

        if (!title.isNullOrBlank() && !fullUrl.isNullOrBlank()) {
            InfoData(
                title = title,
                infoUrl = fullUrl
            )
        } else null
    }
}