package com.hiosdra.hreader.di

import com.hiosdra.hreader.ui.feeds.FeedsViewModel
import com.hiosdra.hreader.ui.main.MainViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MainViewModel(get()) }
    viewModel { FeedsViewModel(get()) }
}
