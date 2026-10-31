package com.lmqr.ha9_comp_service.colors

import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import com.lmqr.ha9_comp_service.R
import com.lmqr.ha9_comp_service.databinding.ColorSliderRowBinding
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding

fun FloatingMenuLayoutBinding.bindColors(manager: AppColorsManager) {
    recolorToggle.setOnClickListener { recolorToggle.showOnOff(manager.toggleRecolor()) }
    colorsReset.setOnClickListener {
        manager.resetApp()
        showColors(manager)
    }
    lineThreshold.bind(R.string.text_and_icons, onPreview = {}) {
        manager.saveAppValue(ColorKeys.LINE_THRESHOLD, it)
    }
    listOf(
        Triple(panelContrast, R.string.panel_contrast, ColorKeys.EPD_CONTRAST),
        Triple(panelBlack, R.string.panel_black, ColorKeys.EPD_BLACK),
        Triple(panelWhite, R.string.panel_white, ColorKeys.EPD_WHITE),
    ).forEach { (row, label, key) ->
        row.bind(label, onPreview = { manager.previewPanelLevels(slidersPanelLevels()) }) {
            manager.saveAppValue(key, it)
        }
    }
    showColors(manager)
}

fun FloatingMenuLayoutBinding?.showColors(manager: AppColorsManager) = this?.run {
    recolorToggle.showOnOff(manager.isRecolorEnabled)
    lineThreshold.show(manager.lineThreshold)
    manager.panelLevels.let {
        panelContrast.show(it.contrast)
        panelBlack.show(it.black)
        panelWhite.show(it.white)
    }
}

private fun FloatingMenuLayoutBinding.slidersPanelLevels() = PanelLevels(
    contrast = panelContrast.seekbar.progress,
    black = panelBlack.seekbar.progress,
    white = panelWhite.seekbar.progress,
)

private fun ColorSliderRowBinding.bind(
    @StringRes labelRes: Int,
    onPreview: (Int) -> Unit,
    onSave: (Int) -> Unit,
) {
    label.setText(labelRes)
    seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        private var progressOnTouch = 0

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            value.text = progress.toString()
            if (fromUser)
                onPreview(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            progressOnTouch = seekBar.progress
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (seekBar.progress != progressOnTouch)
                onSave(seekBar.progress)
        }
    })
}

private fun ColorSliderRowBinding.show(progress: Int) {
    seekbar.progress = progress
    value.text = progress.toString()
}

private fun TextView.showOnOff(isOn: Boolean) {
    text = if (isOn) "ON" else "OFF"
}
