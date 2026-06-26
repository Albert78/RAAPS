package de.dh.raaps.common.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

class NavigationViewModel(
    initialStack: List<NavKey>
) : ViewModel() {

    private val _backstack = MutableStateFlow(initialStack)
    val backstack: StateFlow<List<NavKey>> = _backstack

    val currentRoute: NavKey
        get() = _backstack.value.last()

    fun push(route: NavKey) {
        _backstack.update { it + route }
    }

    fun pop(): Boolean {
        return _backstack.updateAndGet {
            if (it.size > 1) it.dropLast(1) else it
        }.size > 1
    }

    fun replaceTop(route: NavKey) {
        _backstack.update { it.dropLast(1) + route }
    }

    fun reset(stack: List<NavKey>) {
        _backstack.value = stack
    }

    companion object {
        class NavigationViewModelFactory(
            private val initialStack: List<NavKey>
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NavigationViewModel(initialStack) as T
            }
        }
    }
}