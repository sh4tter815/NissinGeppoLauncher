package com.example.nissingeppolauncher.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp


@Composable
fun DefaultLauncherDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current

    BlurDialog(onDismissRequest = onDismiss) {
        BlurDialogTitle("デフォルトアプリに設定", fontFamily = fontFamily, fontWeight = fontWeight)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "「日進月歩ランチャー」をデフォルトのホームアプリ（ホーム画面）として設定しますか？",
            color = Color.LightGray,
            fontSize = 14.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
        BlurDialogButtonRow(
            confirmText = "設定を開く",
            onConfirm = {
                openDefaultHomeSettings(context)
                onDismiss()
            },
            dismissText = "あとで",
            onDismiss = onDismiss,
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
    }
}

fun openDefaultHomeSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        context.startActivity(intent)
    }
}
