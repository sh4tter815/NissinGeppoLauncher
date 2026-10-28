package com.example.geppolauncher.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.geppolauncher.data.AppInfo

import androidx.compose.ui.res.stringResource
import com.example.geppolauncher.R

@Composable
fun AppOptionDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onSetYomi: (AppInfo, String) -> Unit,
    onAddAlias: (AppInfo, String) -> Unit,
    onRemoveAlias: (AppInfo, String) -> Unit,
    onToggleHide: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var showYomiInputDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var yomiInputText by remember { mutableStateOf(app.customYomi) }
    var aliasInputText by remember { mutableStateOf("") }

    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current
    val textStyle = TextStyle(fontFamily = fontFamily, fontWeight = fontWeight)

    if (showYomiInputDialog) {
        AlertDialog(
            onDismissRequest = { showYomiInputDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetYomi(app, yomiInputText)
                        showYomiInputDialog = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.save), color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYomiInputDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text(stringResource(R.string.yomi_registration_title), fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.yomi_registration_message, app.label),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = yomiInputText,
                        onValueChange = { yomiInputText = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.yomi_placeholder), color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aliasInputText.isNotBlank()) {
                            onAddAlias(app, aliasInputText)
                            aliasInputText = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.add), color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false; onDismiss() }) {
                    Text(stringResource(R.string.done), color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text(stringResource(R.string.alias_add_title), fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.alias_add_message, app.label),
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInputText,
                        onValueChange = { aliasInputText = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.alias_placeholder), color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    if (app.searchAliases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.registered_aliases),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            items(app.searchAliases) { alias ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "・ $alias",
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight
                                    )
                                    Text(
                                        text = stringResource(R.string.delete),
                                        color = Color(0xFFFF8888),
                                        fontSize = 13.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight,
                                        modifier = Modifier.clickable {
                                            onRemoveAlias(app, alias)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close), color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text(text = app.label, fontSize = 20.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialogOptionItem(
                        text = if (app.isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite),
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleFavorite(app)
                        onDismiss()
                    }

                    DialogOptionItem(
                        text = if (app.customYomi.isNotBlank()) stringResource(R.string.change_yomi, app.customYomi) else stringResource(R.string.register_yomi),
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showYomiInputDialog = true
                    }

                    DialogOptionItem(
                        text = if (app.searchAliases.isNotEmpty()) stringResource(R.string.searchable_aliases_count, app.searchAliases.size) else stringResource(R.string.add_searchable_alias),
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showAliasDialog = true
                    }

                    DialogOptionItem(
                        text = if (app.isHidden) stringResource(R.string.unhide_app) else stringResource(R.string.hide_app),
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleHide(app)
                        onDismiss()
                    }

                    DialogOptionItem(text = stringResource(R.string.app_info), fontFamily = fontFamily, fontWeight = fontWeight) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${app.packageName}".toUri()
                        )
                        context.startActivity(intent)
                        onDismiss()
                    }
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    }
}

@Composable
private fun DialogOptionItem(
    text: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: androidx.compose.ui.text.font.FontWeight,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E283D),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight
            )
        }
    }
}
