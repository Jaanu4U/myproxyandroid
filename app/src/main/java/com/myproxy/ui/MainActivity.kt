package com.myproxy.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myproxy.R
import com.myproxy.api.ApiResponse
import com.myproxy.api.MyProxyAPI
import com.myproxy.api.ProxyConfig
import com.myproxy.vpn.ProxyServerService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var myProxyAPI: MyProxyAPI
    private var proxyService: ProxyServerService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ProxyServerService.ProxyServerBinder
            proxyService = binder.getService()
            isBound = true
            
            // Set up status listener
            proxyService?.setStatusListener { isRunning, message ->
                runOnUiThread {
                    showToast(message)
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize API client
        myProxyAPI = MyProxyAPI(this)
        
        // Bind to proxy service
        bindProxyService()
        
        // Check if user is already logged in
        if (myProxyAPI.isAuthenticated()) {
            startDashboard()
        } else {
            startLogin()
        }
    }
    
    private fun bindProxyService() {
        val intent = Intent(this, ProxyServerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun startLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun startDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
