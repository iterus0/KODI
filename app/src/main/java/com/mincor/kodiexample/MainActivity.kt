package com.mincor.kodiexample

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.mincor.kodi.core.IKodi
import com.mincor.kodi.core.kodi


class MainActivity : AppCompatActivity(), IKodi {

    val kodi = kodi {

    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
