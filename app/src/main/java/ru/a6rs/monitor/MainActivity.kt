package ru.a6rs.monitor

import android.app.*
import android.os.*
import android.view.*
import android.graphics.*
import android.widget.*
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView
import android.text.InputType
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var screen: Dash
    private lateinit var mapView: MapView
    private val pool = Executors.newSingleThreadScheduledExecutor()
    private var host = "http://10.167.219.15"
    private var user = "admin"
    private var pass = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do not force immersive fullscreen: Android needs normal window controls for split-screen.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        val pref = getSharedPreferences("cfg", 0)
        host = pref.getString("host", host) ?: host
        user = pref.getString("user", user) ?: user
        pass = pref.getString("pass", pass) ?: pass
        if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
            Toast.makeText(this, "Не задан ключ Яндекс MapKit", Toast.LENGTH_LONG).show()
        } else {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
        }
        MapKitFactory.initialize(this)
        mapView = MapView(this)
        MapKitFactory.getInstance().createTrafficLayer(mapView.mapWindow).isTrafficVisible = true
        screen = Dash(this, { openNav() }, { settings() })
        val layout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        layout.addView(mapView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f))
        layout.addView(screen, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        setContentView(layout)
        pool.scheduleAtFixedRate({ poll() }, 0, 1500, TimeUnit.MILLISECONDS)
    }
    private fun poll() {
        var c: HttpURLConnection? = null
        try {
            c = URL(host.trimEnd('/') + "/api/status").openConnection() as HttpURLConnection
            c.connectTimeout = 2500; c.readTimeout = 2500
            c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP))
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val d = Telemetry(j.optDouble("fuel", Double.NaN), j.optDouble("temp", Double.NaN),
                j.optDouble("volt", Double.NaN), j.optDouble("odo", Double.NaN),
                j.optBoolean("kline", false), j.optBoolean("torque", false), true)
            runOnUiThread { screen.set(d) }
        } catch (_: Exception) { runOnUiThread { screen.set(screen.data.copy(online = false)) } }
        finally { c?.disconnect() }
    }
    private fun openNav() {
        val i = packageManager.getLaunchIntentForPackage("ru.yandex.yandexnavi")
        if (i != null) startActivity(i) else try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("yandexnavi://")))
        } catch (_: Exception) { Toast.makeText(this, "Установи Яндекс Навигатор", Toast.LENGTH_LONG).show() }
    }
    private fun settings() {
        val box = LinearLayout(this).apply { orientation = 1; setPadding(30, 8, 30, 0) }
        fun field(title: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            box.addView(TextView(this).apply { text = title })
            return EditText(this).also { it.setSingleLine(); it.setText(value); it.inputType = type; box.addView(it) }
        }
        val h = field("Адрес ESP32", host)
        val u = field("Логин", user)
        val p = field("Пароль", pass, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        AlertDialog.Builder(this).setTitle("Настройки ESP32").setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                host = h.text.toString().trim().trimEnd('/'); user = u.text.toString(); pass = p.text.toString()
                getSharedPreferences("cfg", 0).edit().putString("host", host).putString("user", user).putString("pass", pass).apply()
            }.setNegativeButton("Отмена", null).show()
    }
    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }
    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }
}

data class Telemetry(val fuel: Double = Double.NaN, val temp: Double = Double.NaN,
    val volt: Double = Double.NaN, val odo: Double = Double.NaN,
    val kline: Boolean = false, val torque: Boolean = false, val online: Boolean = false)

class Dash(ctx: android.content.Context, val nav: () -> Unit, val cfg: () -> Unit) : View(ctx) {
    var data = Telemetry()
    private val p = Paint(3)
    private val red = Color.rgb(255, 24, 35)
    private val white = Color.WHITE
    private val green = Color.rgb(0, 235, 45)
    fun set(d: Telemetry) { data = d; postInvalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.rgb(5, 6, 9))
        val narrow = w / h < 0.95f
        if (narrow) drawSidePanel(c, w, h) else drawWidePanel(c, w, h)
    }

    private fun drawSidePanel(c: Canvas, w: Float, h: Float) {
        val pad = w * .075f
        val left = pad
        val right = w - pad
        val headerH = h * .105f
        p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(20, 20, 23), Color.rgb(3, 4, 6), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        p.color = red; p.strokeWidth = maxOf(2f, w * .008f); c.drawLine(left, headerH, right, headerH, p)
        label(c, "AUDI", w*.5f, h*.045f, minOf(w*.10f,h*.035f), white, true, Paint.Align.CENTER)
        label(c, "RS", w*.5f, h*.083f, minOf(w*.12f,h*.043f), red, true, Paint.Align.CENTER)

        val top = headerH + h*.025f
        val bottom = h*.91f
        val gap = h*.012f
        val cardH = (bottom - top - gap*5) / 6f
        metricCard(c, "ТОПЛИВО", fmt(data.fuel, "%"), "FUEL", left, top, right, top+cardH, 0)
        metricCard(c, "ТЕМП. ОЖ", fmt(data.temp, "°C"), "COOLANT", left, top+(cardH+gap), right, top+2*cardH+gap, 1)
        metricCard(c, "НАПРЯЖЕНИЕ", fmt(data.volt, " V"), "VOLTAGE", left, top+2*(cardH+gap), right, top+3*cardH+2*gap, 2)
        metricCard(c, "ПРОБЕГ", fmt(data.odo, " км"), "ODOMETER", left, top+3*(cardH+gap), right, top+4*cardH+3*gap, 3)

        val statusY = top+4*(cardH+gap)
        val statusH = cardH*1.35f
        p.color = Color.rgb(32, 34, 39); c.drawRoundRect(left,statusY,right,statusY+statusH,10f,10f,p)
        label(c, "ESP32", w*.5f, statusY+statusH*.34f, minOf(w*.085f,statusH*.22f), Color.LTGRAY,true,Paint.Align.CENTER)
        label(c, if(data.online) "● ОНЛАЙН" else "● НЕТ СВЯЗИ", w*.5f, statusY+statusH*.68f, minOf(w*.078f,statusH*.20f), if(data.online)green else red,true,Paint.Align.CENTER)
        val klY = statusY+statusH+gap
        p.color = Color.rgb(32,34,39); c.drawRoundRect(left,klY,right,bottom,10f,10f,p)
        label(c,"K-LINE",w*.5f,klY+(bottom-klY)*.38f,minOf(w*.085f,(bottom-klY)*.23f),Color.LTGRAY,true,Paint.Align.CENTER)
        label(c,if(data.kline)"● OK" else "● НЕТ СВЯЗИ",w*.5f,klY+(bottom-klY)*.72f,minOf(w*.08f,(bottom-klY)*.22f),if(data.kline)green else red,true,Paint.Align.CENTER)

        p.color=red; p.strokeWidth=maxOf(2f,w*.008f); c.drawLine(left,h*.95f,right,h*.95f,p)
        label(c,"НАСТРОЙКИ  ⚙",w*.5f,h*.985f,minOf(w*.075f,h*.025f),Color.LTGRAY,true,Paint.Align.CENTER)
    }

    private fun metricCard(c:Canvas,title:String,value:String,sub:String,l:Float,t:Float,r:Float,b:Float,index:Int) {
        p.color = Color.rgb(29,31,35); c.drawRoundRect(l,t,r,b,10f,10f,p)
        p.color = red; c.drawRoundRect(l,t,l+maxOf(3f,width*.012f),b,4f,4f,p)
        val cx=(l+r)*.5f
        val titleSize=minOf(width*.075f,(b-t)*.22f)
        val valueSize=minOf(width*.125f,(b-t)*.39f)
        label(c,title,cx,t+(b-t)*.29f,titleSize,Color.LTGRAY,true,Paint.Align.CENTER)
        label(c,value,cx,t+(b-t)*.70f,valueSize,white,true,Paint.Align.CENTER)
    }

    private fun drawWidePanel(c: Canvas, w: Float, h: Float) {
        p.shader = LinearGradient(0f,0f,w,h,Color.rgb(18,20,24),Color.rgb(3,4,6),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p); p.shader=null
        label(c,"AUDI  A6  RS MONITOR",w*.5f,h*.12f,h*.07f,white,true,Paint.Align.CENTER)
        p.color=red;p.strokeWidth=3f;c.drawLine(w*.06f,h*.19f,w*.94f,h*.19f,p)
        val items=listOf(Triple("ТОПЛИВО",fmt(data.fuel,"%"),"FUEL"),Triple("ТЕМПЕРАТУРА ОЖ",fmt(data.temp,"°C"),"COOLANT"),Triple("НАПРЯЖЕНИЕ",fmt(data.volt," V"),"VOLTAGE"),Triple("ПРОБЕГ",fmt(data.odo," км"),"ODOMETER"))
        val cardW=w*.43f; val cardH=h*.24f
        items.forEachIndexed { i,item ->
            val col=i%2; val row=i/2
            val l=w*.05f+col*(cardW+w*.04f); val t=h*.25f+row*(cardH+h*.06f)
            metricCard(c,item.first,item.second,item.third,l,t,l+cardW,t+cardH,i)
        }
        label(c,if(data.online)"ESP32 ● ОНЛАЙН" else "ESP32 ● НЕТ СВЯЗИ",w*.28f,h*.88f,h*.045f,if(data.online)green else red,true,Paint.Align.CENTER)
        label(c,if(data.kline)"K-LINE ● OK" else "K-LINE ● НЕТ СВЯЗИ",w*.72f,h*.88f,h*.045f,if(data.kline)green else red,true,Paint.Align.CENTER)
        label(c,"⚙ НАСТРОЙКИ",w*.5f,h*.97f,h*.035f,Color.LTGRAY,true,Paint.Align.CENTER)
    }

    private fun fmt(v:Double,s:String)=if(v.isNaN()) "--$s" else String.format(java.util.Locale.US,"%.0f%s",v,s)
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean,align:Paint.Align=Paint.Align.LEFT) {
        p.color=color;p.textSize=size.coerceAtLeast(9f);p.textAlign=align;p.typeface=if(bold) Typeface.create("sans-serif-condensed",Typeface.BOLD) else Typeface.DEFAULT;c.drawText(s,x,y,p)
    }
    override fun onTouchEvent(e: MotionEvent):Boolean {
        if(e.action==MotionEvent.ACTION_UP) {
            val x=e.x/width; val y=e.y/height
            if (width.toFloat()/height.toFloat() < .95f) {
                if (y > .94f) cfg()
            } else if (y > .91f) cfg()
            return true
        }
        return true
    }
}
