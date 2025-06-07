package com.example.mymediasessiontest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mymediasessiontest.ui.theme.MyMediaSessionTestTheme
import android.graphics.BitmapFactory
import android.content.Intent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MediaViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启动媒体监控服务
        val serviceIntent = Intent(this, CarMediaMonitorService::class.java)
        startService(serviceIntent)
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        
        enableEdgeToEdge()
        setContent {
            MyMediaSessionTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaPlayerScreen(viewModel)
                }
            }
        }
    }
    
    override fun onDestroy() {
        // 停止服务
        val serviceIntent = Intent(this, CarMediaMonitorService::class.java)
        stopService(serviceIntent)
        super.onDestroy()
    }
}

@Composable
fun MediaPlayerScreen(viewModel: MediaViewModel = viewModel()) {
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "车载媒体播放器",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 专辑封面和基本信息区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 专辑封面
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaInfo.albumArtBytes != null) {
                        val bitmap = remember(mediaInfo.albumArtBytes) {
                            BitmapFactory.decodeByteArray(
                                mediaInfo.albumArtBytes,
                                0,
                                mediaInfo.albumArtBytes!!.size
                            )?.asImageBitmap()
                        }
                        
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "专辑封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // 默认图片
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "默认专辑封面",
                                modifier = Modifier.size(100.dp),
                                tint = Color.White
                            )
                        }
                    } else {
                        // 默认图片
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "默认专辑封面",
                            modifier = Modifier.size(100.dp),
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 歌曲信息
                Text(
                    text = mediaInfo.title.ifEmpty { "未播放" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = mediaInfo.artist.ifEmpty { "未知艺术家" },
                    fontSize = 18.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = mediaInfo.album.ifEmpty { "未知专辑" },
                    fontSize = 16.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 进度条
                val progress = if (mediaInfo.duration > 0) {
                    mediaInfo.position.toFloat() / mediaInfo.duration.toFloat()
                } else {
                    0f
                }
                
                // 修改 LinearProgressIndicator 的用法
                LinearProgressIndicator(
                    progress = progress,  // 移除 lambda 表达式
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(mediaInfo.position),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    Text(
                        text = formatDuration(mediaInfo.duration),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 播放状态
                Text(
                    text = if (mediaInfo.isPlaying) "正在播放" else "已暂停",
                    fontSize = 16.sp,
                    color = if (mediaInfo.isPlaying) Color.Green else Color.Red
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 控制按钮区
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 快退按钮
                    IconButton(
                        onClick = { viewModel.rewind() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rewind),
                            contentDescription = "快退",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                    
                    // 上一曲按钮
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_previous),
                            contentDescription = "上一曲",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                    
                    // 播放/暂停按钮
                    IconButton(
                        onClick = { 
                            if (mediaInfo.isPlaying) viewModel.pause() else viewModel.play() 
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (mediaInfo.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (mediaInfo.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                    
                    // 下一曲按钮
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "下一曲",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                    
                    // 快进按钮
                    IconButton(
                        onClick = { viewModel.fastForward() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fast_forward),
                            contentDescription = "快进",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        // 歌词显示区域
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp)
            ) {
                if (mediaInfo.lyrics.isEmpty()) {
                    // 无歌词时显示提示
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无歌词",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 有歌词时显示歌词列表
                    LyricsDisplay(
                        lyrics = mediaInfo.lyrics,
                        currentLyricIndex = mediaInfo.currentLyricIndex
                    )
                }
            }
        }
        
        // 控制按钮区
        // 在MediaPlayerScreen函数中，添加媒体控制按钮部分
        // 在进度条和时间显示之后添加
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 媒体控制按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 快退按钮
            IconButton(
                onClick = { viewModel.rewind() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rewind),
                    contentDescription = "快退",
                    modifier = Modifier.size(36.dp)
                )
            }
            
            // 上一曲按钮
            IconButton(
                onClick = { viewModel.previous() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = "上一曲",
                    modifier = Modifier.size(36.dp)
                )
            }
            
            // 播放/暂停按钮
            IconButton(
                onClick = { 
                    if (mediaInfo.isPlaying) viewModel.pause() else viewModel.play() 
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (mediaInfo.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (mediaInfo.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // 下一曲按钮
            IconButton(
                onClick = { viewModel.next() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "下一曲",
                    modifier = Modifier.size(36.dp)
                )
            }
            
            // 快进按钮
            IconButton(
                onClick = { viewModel.fastForward() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fast_forward),
                    contentDescription = "快进",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun LyricsDisplay(
    lyrics: List<LyricLine>,
    currentLyricIndex: Int
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 当当前歌词索引变化时，滚动到对应位置
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = currentLyricIndex.coerceAtMost(lyrics.size - 1),
                    scrollOffset = -100 // 偏移量，使当前歌词显示在中间位置
                )
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(lyrics) { index, lyric ->
            val isCurrentLyric = index == currentLyricIndex
            
            Text(
                text = lyric.text,
                fontSize = if (isCurrentLyric) 20.sp else 16.sp,
                fontWeight = if (isCurrentLyric) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentLyric) Color.Blue else Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

// 格式化时间为 mm:ss 格式
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}