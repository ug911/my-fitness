package com.ug911.myfitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ug911.myfitness.ui.MyFitnessRoot
import com.ug911.myfitness.ui.theme.MyFitnessTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MyFitnessApp).container
        setContent {
            MyFitnessTheme {
                MyFitnessRoot(container = container)
            }
        }
    }
}
