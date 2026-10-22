package com.example.toolkit.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.toolkit.ui.components.CategoryPage
import com.example.toolkit.ui.components.DetailOverlay
import com.example.toolkit.ui.components.ExclusivePage
import com.example.toolkit.ui.components.FavoritesPage
import com.example.toolkit.ui.components.HomePage
import com.example.toolkit.ui.components.LoginOverlay
import com.example.toolkit.ui.components.SearchOverlay
import com.example.toolkit.ui.components.TabBar
import com.example.toolkit.ui.components.TopBar
import com.example.toolkit.ui.components.UserMenuOverlay
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.FlowingBackground
import com.example.toolkit.ui.design.TopFade
import com.example.toolkit.ui.model.AppTab
import com.example.toolkit.ui.model.ToolCatalog
import com.example.toolkit.ui.model.ToolItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS = "toolkit_prefs"
private const val USER_KEY = "user_name"

@Composable
fun ToolkitApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTabName by rememberSaveable { mutableStateOf(AppTab.Home.name) }
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(ToolCatalog.categories.first().id)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var detailTool by remember { mutableStateOf<ToolItem?>(null) }

    var loginSheetOpen by remember { mutableStateOf(false) }
    var loginBusy by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf(prefs.getString(USER_KEY, null)) }
    var userMenuOpen by remember { mutableStateOf(false) }

    // Favorites are session scoped in the prototype (no localStorage entry).
    val favoriteNames = remember { mutableStateListOf<String>() }

    val selectedTab = AppTab.entries.firstOrNull { it.name == selectedTabName } ?: AppTab.Home
    val favorites = favoriteNames.mapNotNull(ToolCatalog.byName::get)
    val favoriteSet = favoriteNames.toSet()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val topBarTop = statusBarTop + 8.dp
    val topBarBottom = topBarTop + 54.dp
    val contentTop = topBarBottom + 14.dp
    val tabBarBottomMargin = navigationBarBottom + 12.dp
    val pageBottomInset = tabBarBottomMargin + 64.dp + 28.dp
    val categoryBottomInset = tabBarBottomMargin + 64.dp + 10.dp
    val pageTopInset = contentTop - statusBarTop
    val userMenuTop = topBarBottom + 2.dp

    val baseBlurRadius = when {
        detailTool != null -> 18.dp
        loginSheetOpen -> 6.dp
        else -> 0.dp
    }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val pagesBlurRadius = if (searchOpen && blurSupported) 12.dp else 0.dp

    val baseBlurModifier = if (baseBlurRadius > 0.dp && blurSupported) {
        Modifier.blur(baseBlurRadius)
    } else {
        Modifier
    }
    val pagesBlurModifier = if (pagesBlurRadius > 0.dp) {
        Modifier.blur(pagesBlurRadius)
    } else {
        Modifier
    }

    fun closeSearch() {
        searchOpen = false
        searchFocused = false
        focusManager.clearFocus()
    }

    fun openTool(tool: ToolItem) {
        closeSearch()
        userMenuOpen = false
        detailTool = tool
    }

    fun switchTab(tab: AppTab) {
        closeSearch()
        userMenuOpen = false
        detailTool = null
        if (tab != selectedTab) selectedTabName = tab.name
    }

    fun toggleFavorite(name: String) {
        if (!favoriteNames.remove(name)) favoriteNames.add(name)
    }

    fun handleAccountClick() {
        closeSearch()
        if (user != null) {
            userMenuOpen = !userMenuOpen
        } else {
            loginSheetOpen = true
        }
    }

    fun handleWeChatLogin() {
        if (loginBusy) return
        loginBusy = true
        scope.launch {
            delay(750)
            user = "河马同学"
            prefs.edit().putString(USER_KEY, "河马同学").apply()
            loginBusy = false
            loginSheetOpen = false
        }
    }

    fun logout() {
        user = null
        prefs.edit().remove(USER_KEY).apply()
        userMenuOpen = false
    }

    BackHandler(
        enabled = loginSheetOpen || userMenuOpen || detailTool != null || searchOpen,
    ) {
        when {
            loginSheetOpen -> loginSheetOpen = false
            userMenuOpen -> userMenuOpen = false
            detailTool != null -> detailTool = null
            searchOpen -> closeSearch()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Everything in this layer gets blurred while a modal surface is open.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(baseBlurModifier),
        ) {
            FlowingBackground(Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .then(pagesBlurModifier),
            ) {
                val density = LocalDensity.current
                val pageOffset = with(density) { 12.dp.roundToPx() }

                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (
                            fadeIn(tween(460, easing = EmphasizedEasing)) +
                                slideInVertically(
                                    animationSpec = tween(460, easing = EmphasizedEasing),
                                    initialOffsetY = { pageOffset },
                                ) +
                                scaleIn(
                                    animationSpec = tween(460, easing = EmphasizedEasing),
                                    initialScale = 0.992f,
                                )
                            ) togetherWith fadeOut(tween(120))
                    },
                    label = "tabPages",
                ) { tab ->
                    when (tab) {
                        AppTab.Home -> HomePage(
                            categories = ToolCatalog.categories,
                            onToolClick = { openTool(it) },
                            topInset = pageTopInset,
                            bottomInset = pageBottomInset,
                        )

                        AppTab.Favorites -> FavoritesPage(
                            favorites = favorites,
                            onToolClick = { openTool(it) },
                            topInset = pageTopInset,
                            bottomInset = pageBottomInset,
                        )

                        AppTab.Exclusive -> ExclusivePage(
                            onToolClick = { openTool(it) },
                            topInset = pageTopInset,
                            bottomInset = pageBottomInset,
                        )

                        AppTab.Category -> CategoryPage(
                            categories = ToolCatalog.categories,
                            selectedId = selectedCategoryId,
                            onSelectCategory = { selectedCategoryId = it },
                            onToolClick = { openTool(it) },
                            topInset = pageTopInset,
                            bottomInset = categoryBottomInset,
                        )
                    }
                }
            }

            TopFade(Modifier.align(Alignment.TopStart))

            SearchOverlay(
                visible = searchOpen,
                query = query,
                allTools = ToolCatalog.allTools,
                onToolClick = { openTool(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarBottom),
            )

            TopBar(
                query = query,
                onQueryChange = {
                    query = it
                    searchOpen = true
                },
                searchFocused = searchFocused,
                onSearchFocusChanged = { focused ->
                    searchFocused = focused
                    if (focused) {
                        searchOpen = true
                        userMenuOpen = false
                    }
                },
                onClearQuery = {
                    query = ""
                    closeSearch()
                },
                loggedIn = user != null,
                onAccountClick = { handleAccountClick() },
                onBrandClick = {
                    closeSearch()
                    userMenuOpen = false
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = topBarTop),
            )

            TabBar(
                selected = selectedTab,
                onSelect = { switchTab(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = tabBarBottomMargin),
            )
        }

        DetailOverlay(
            visible = detailTool != null,
            tool = detailTool,
            isFavorite = detailTool?.let { it.name in favoriteSet } == true,
            statusBarTop = statusBarTop,
            navigationBarBottom = navigationBarBottom,
            onBack = { detailTool = null },
            onToggleFavorite = {
                detailTool?.let { toggleFavorite(it.name) }
            },
        )

        UserMenuOverlay(
            visible = userMenuOpen && user != null,
            userName = user.orEmpty(),
            topOffset = userMenuTop,
            onDismiss = { userMenuOpen = false },
            onLogout = { logout() },
        )

        LoginOverlay(
            visible = loginSheetOpen,
            busy = loginBusy,
            navigationBarBottom = navigationBarBottom,
            onWeChatLogin = { handleWeChatLogin() },
            onDismiss = { loginSheetOpen = false },
        )
    }
}
