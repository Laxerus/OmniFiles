package dev.laxerus.omnifiles.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import dev.laxerus.omnifiles.R

class SettingsActionCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val icon: ImageView
    private val title: TextView
    private val summary: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_settings_action_card, this, true)
        icon = findViewById(R.id.actionIcon)
        title = findViewById(R.id.actionTitle)
        summary = findViewById(R.id.actionSummary)

        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0),
        )
        setCardBackgroundColor(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0),
            )
        )
        isClickable = true
        isFocusable = true
        minimumHeight = dp(88)

        val selectable = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)) {
            foreground = AppCompatResources.getDrawable(context, selectable.resourceId)
        }
    }

    fun bind(@StringRes titleRes: Int, @StringRes summaryRes: Int, @DrawableRes iconRes: Int) {
        title.setText(titleRes)
        summary.setText(summaryRes)
        icon.setImageResource(iconRes)
        updateAccessibility()
    }

    fun setSummary(@StringRes summaryRes: Int) {
        summary.setText(summaryRes)
        updateAccessibility()
    }

    fun setSummary(value: CharSequence) {
        summary.text = value
        updateAccessibility()
    }

    fun summaryText(): CharSequence = summary.text

    private fun updateAccessibility() {
        val titleText = title.text?.toString().orEmpty()
        val summaryText = summary.text?.toString().orEmpty()
        contentDescription = listOf(titleText, summaryText)
            .filter { it.isNotBlank() }
            .joinToString(". ")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
