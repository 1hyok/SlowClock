package com.example.slowclock.ui.information

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.slowclock.ui.information.component.InfoList

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun InformationScreen(infoViewModel: InfoViewModel = hiltViewModel()) {
    val infoList = infoViewModel.infoList
    val isLoading = infoViewModel.isLoading.value
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = isLoading,
            onRefresh = { infoViewModel.fetchInfo() },
        )
    LaunchedEffect(Unit) {
        infoViewModel.fetchInfo()
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        InfoList(list = infoList)
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
