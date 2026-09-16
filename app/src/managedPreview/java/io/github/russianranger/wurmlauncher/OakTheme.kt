package io.github.russianranger.wurmlauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Static launcher decoration. No timers, game textures or service ownership. */
object OakTheme {
    private val scenes = intArrayOf(R.drawable.scene_server, R.drawable.scene_client,
        R.drawable.scene_mods, R.drawable.scene_diagnostics)
    private val titles = arrayOf("The homestead", "Beyond the gates", "The workshop", "The map room")
    private val subtitles = arrayOf("Tend your world", "Your next adventure awaits",
        "Shape your adventure", "Keep watch over your world")

    fun heading(view: TextView) {
        view.typeface = view.resources.getFont(R.font.medieval_sharp)
        view.setTextColor(view.context.getColor(R.color.wurm_accent))
        view.isAccessibilityHeading = true
    }

    fun header(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = LauncherUi.dp(context, 12)
        setPadding(p, p / 2, p, p / 2)
        setBackgroundColor(context.getColor(R.color.wurm_background))
        addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_oak)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LauncherUi.dp(context, 48), LauncherUi.dp(context, 48)))
        addView(TextView(context).apply {
            text = "Wurm"
            textSize = 28f
            setPadding(p, 0, p, 0)
            heading(this)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply {
            text = "0.10.51"
            textSize = 12f
            setTextColor(context.getColor(R.color.wurm_text_secondary))
        })
    }

    /** Center readable panels on wide screens; allow natural height and font scaling. */
    fun page(body: View, tab: Int): View {
        val context = body.context
        return FrameLayout(context).apply {
            val column = object : LinearLayout(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val width = minOf(View.MeasureSpec.getSize(widthMeasureSpec), LauncherUi.dp(context, 760))
                    super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                val p = LauncherUi.dp(context, 16)
                setPadding(p, 0, p, p)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.BOTTOM
                    minimumHeight = LauncherUi.dp(context, 132)
                    setPadding(p / 2, p, p / 2, p)
                    addView(TextView(context).apply {
                        text = titles[tab]; textSize = 28f; heading(this)
                        setShadowLayer(5f, 0f, 2f, Color.BLACK)
                    })
                    addView(TextView(context).apply {
                        text = subtitles[tab]; textSize = 16f
                        setShadowLayer(4f, 0f, 2f, Color.BLACK)
                    })
                })
                body.setBackgroundResource(R.drawable.wurm_stone_panel)
                body.setPadding(p, p, p, p)
                addView(body, LinearLayout.LayoutParams(-1, -2))
            }
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
    }

    /** Only the visible scene has a bitmap reference; release it when the launcher stops. */
    class Backdrop(context: Context) : ImageView(context) {
        private var shown = -1
        init {
            scaleType = ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun show(tab: Int) {
            if (shown == tab) return
            // decodeResource avoids the Resources drawable bitmap cache; no hidden-tab bitmaps.
            setImageBitmap(BitmapFactory.decodeResource(resources, scenes[tab],
                BitmapFactory.Options().apply { inScaled = false }))
            shown = tab
        }
        fun release() { setImageDrawable(null); shown = -1 }
    }
}
