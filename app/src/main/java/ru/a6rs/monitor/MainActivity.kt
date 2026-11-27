package ru.a6rs.monitor

import android.app.*
import android.os.*
import android.view.*
import android.graphics.*
import android.widget.*
import android.text.InputType
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var screen: Dash
    private val pool = Executors.newSingleThreadScheduledExecutor()
    private var host = "http://10.167.219.15"
    private var user = "admin"
    private var pass = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512 or 4096
        val pref = getSharedPreferences("cfg", 0)
        host = pref.getString("host", host) ?: host
        user = pref.getString("user", user) ?: user
        pass = pref.getString("pass", pass) ?: pass
        screen = Dash(this, { openNav() }, { settings() })
        setContentView(screen)
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
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(4, 8, 14))
        p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(12, 31, 47), Color.rgb(3, 8, 13), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        p.color = Color.argb(75, 120, 155, 175); p.strokeWidth = 2f
        for (i in 0..10) { val y = h*(.13f+i*.075f); c.drawLine(0f,y,w,y+((i-5)*18),p) }
        p.color = Color.rgb(88, 240, 15); p.strokeWidth = h*.009f; p.style = Paint.Style.STROKE
        p.setShadowLayer(14f,0f,0f,green)
        val path = Path().apply { moveTo(w*.50f,h*.90f); cubicTo(w*.46f,h*.67f,w*.60f,h*.57f,w*.54f,h*.39f); cubicTo(w*.50f,h*.25f,w*.61f,h*.18f,w*.64f,h*.11f) }
        c.drawPath(path,p); p.clearShadowLayer(); p.style = Paint.Style.FILL
        label(c,"14:37",w*.035f,h*.055f,h*.035f,white,true)
        label(c,"AUDI  A6  RS MONITOR",w*.5f,h*.055f,h*.034f,white,true,Paint.Align.CENTER)
        label(c,if(data.online) "● WIFI ONLINE" else "● ESP32 OFFLINE",w*.965f,h*.055f,h*.024f,if(data.online)green else red,true,Paint.Align.RIGHT)
        rule(c,0f,h*.09f,w,h*.09f)
        gauge(c,w*.105f,h*.235f,h*.105f,"ТОПЛИВО",data.fuel,"%")
        gauge(c,w*.895f,h*.235f,h*.105f,"ОЖ",data.temp,"°C")
        label(c,"НАВИГАЦИЯ",w*.5f,h*.19f,h*.025f,Color.LTGRAY,true,Paint.Align.CENTER)
        p.color = red; p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        c.drawRoundRect(w*.34f,h*.75f,w*.66f,h*.84f,20f,20f,p); p.style = Paint.Style.FILL
        label(c,"▶  ЯНДЕКС НАВИГАТОР",w*.5f,h*.81f,h*.026f,white,true,Paint.Align.CENTER)
        rule(c,0f,h*.91f,w,h*.91f)
        label(c,fmt(data.volt," V"),w*.16f,h*.965f,h*.033f,white,true,Paint.Align.CENTER)
        label(c,fmt(data.odo," KM"),w*.43f,h*.965f,h*.033f,white,true,Paint.Align.CENTER)
        label(c,if(data.online)"ESP32 ONLINE" else "ESP32 OFFLINE",w*.69f,h*.965f,h*.022f,if(data.online)green else red,true,Paint.Align.CENTER)
        label(c,if(data.kline)"K-LINE OK" else "K-LINE —",w*.88f,h*.965f,h*.022f,if(data.kline)green else Color.LTGRAY,true,Paint.Align.CENTER)
    }
    private fun gauge(c:Canvas,x:Float,y:Float,r:Float,name:String,v:Double,unit:String) {
        p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=red;c.drawCircle(x,y,r,p)
        p.strokeWidth=1.5f;p.color=Color.GRAY;c.drawCircle(x,y,r*.82f,p)
        p.style=Paint.Style.FILL
        label(c,name,x,y-r*.38f,r*.23f,Color.LTGRAY,true,Paint.Align.CENTER)
        label(c,fmt(v,unit),x,y+r*.15f,r*.46f,white,true,Paint.Align.CENTER)
    }
    private fun fmt(v:Double,s:String)=if(v.isNaN()) "--$s" else String.format(java.util.Locale.US,"%.0f%s",v,s)
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean,align:Paint.Align=Paint.Align.LEFT) {
        p.color=color;p.textSize=size;p.textAlign=align;p.typeface=if(bold) Typeface.create("sans-serif-condensed",1) else Typeface.DEFAULT;c.drawText(s,x,y,p)
    }
    private fun rule(c:Canvas,x:Float,y:Float,x2:Float,y2:Float) { p.color=red;p.strokeWidth=2.5f;c.drawLine(x,y,x2,y2,p) }
    override fun onTouchEvent(e: android.view.MotionEvent):Boolean {
        if(e.action==MotionEvent.ACTION_UP) {
            val x=e.x/width; val y=e.y/height
            if(y in .72f.. .88f && x in .30f.. .70f) nav()
            else if(y<.10f && x>.90f) cfg()
        }; return true
    }
}
