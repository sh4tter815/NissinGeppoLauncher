package com.example.nissingeppolauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** フォルダに入れるグリフの候補（DiscGlyph の kind 名） */
val FOLDER_GLYPH_KINDS = listOf(
    "music", "disc", "video", "clapperboard", "image", "palette",
    "brush", "sparkles", "sparkle", "star", "tag", "apps",
    "layout-grid", "box", "puzzle", "calendar", "clock", "timer",
    "sun", "droplets", "droplet", "lock", "key-round", "fingerprint",
    "eye", "text", "type", "languages", "database", "download",
    "wallpaper", "guide", "folder"
)

/**
 * フォルダの長押しポップアップ。
 * お気に入りの切替と、フォルダアイコンの中に入れるグリフの選択ができる。
 */
@Composable
fun FolderOptionDialog(
    folderName: String,
    isFavorite: Boolean,
    currentIconKind: String,
    accentColor: Color,
    onToggleFavorite: () -> Unit,
    onPickIcon: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current
    BlurDialog(onDismissRequest = onDismiss) {
        BlurDialogTitle(folderName, fontFamily = fontFamily, fontWeight = fontWeight)
        Spacer(modifier = Modifier.height(14.dp))
        // プレビュー（フォルダ＋現在のグリフ）
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                WhiteFolderIcon(size = 72.dp, color = Color.White)
                if (currentIconKind.isNotBlank()) {
                    Box(modifier = Modifier.offset(y = 7.dp)) {
                        DiscGlyph(currentIconKind, accentColor, sizeDp = 30f)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        // お気に入り切替
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale()
                .clickable { onToggleFavorite() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiscGlyph("star", accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isFavorite) "お気に入りから外す" else "お気に入りに追加",
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "フォルダの中のアイコン",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
        Spacer(modifier = Modifier.height(8.dp))
        val kinds = listOf("") + FOLDER_GLYPH_KINDS
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().height(252.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(kinds.size) { i ->
                val kind = kinds[i]
                val selected = kind == currentIconKind
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .then(if (selected) Modifier.border(1.5.dp, accentColor, RoundedCornerShape(12.dp)) else Modifier)
                        .pressScale()
                        .clickable { onPickIcon(kind.ifBlank { null }) },
                    contentAlignment = Alignment.Center
                ) {
                    if (kind.isBlank()) {
                        DiscGlyph("x", Color.White)
                    } else {
                        DiscGlyph(kind, Color.White)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        }
    }
}
