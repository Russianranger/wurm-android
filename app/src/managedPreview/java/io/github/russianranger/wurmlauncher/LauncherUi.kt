package io.github.russianranger.wurmlauncher

import android.content.Context
import android.view.View
import android.widget.*

/** Small platform-only layout helpers shared by the launcher and game menu. */
object LauncherUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column(context: Context) = LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL
        val p=dp(context,12); setPadding(p,p,p,p)
        isFocusableInTouchMode=true
    }
    fun label(parent: LinearLayout, value: String, size: Float=14f) = TextView(parent.context).apply {
        text=value; textSize=size
        if (size >= 20f) OakTheme.heading(this)
        setPadding(0,dp(context,6),0,dp(context,6)); parent.addView(this)
    }
    fun button(parent: LinearLayout, value: String, action: () -> Unit) = Button(parent.context).apply {
        text=value; isAllCaps=false; minimumHeight=dp(context,48)
        setOnClickListener { action() }
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply {
            topMargin=dp(context,4); bottomMargin=dp(context,4)
        })
    }
    fun section(parent: LinearLayout, title: String, expanded: Boolean=false): LinearLayout {
        val body=LinearLayout(parent.context).apply {
            orientation=LinearLayout.VERTICAL; visibility=if(expanded) View.VISIBLE else View.GONE
            val p=dp(context,8); setPadding(p,p,p,p)
        }
        button(parent,if(expanded) "▾ $title" else "▸ $title") {
            body.visibility=if(body.visibility==View.VISIBLE) View.GONE else View.VISIBLE
            val heading=parent.getChildAt(parent.indexOfChild(body)-1) as Button
            heading.text=(if(body.visibility==View.VISIBLE) "▾ " else "▸ ")+title
        }
        parent.addView(body)
        return body
    }
}
