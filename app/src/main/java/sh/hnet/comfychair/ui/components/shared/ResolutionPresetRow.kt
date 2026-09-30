package sh.hnet.comfychair.ui.components.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/** Quick image resolution presets: label, width, height. */
private val RESOLUTION_PRESETS = listOf(
    Triple("2:3", 832, 1216),
    Triple("1:1", 1024, 1024),
    Triple("3:2", 1216, 832)
)

/**
 * Three toggle buttons to quickly set width/height to a preset.
 * The button matching the current width/height is shown as selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionPresetRow(
    width: String,
    height: String,
    onSelect: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        RESOLUTION_PRESETS.forEachIndexed { index, (label, pw, ph) ->
            SegmentedButton(
                selected = w == pw && h == ph,
                onClick = { onSelect(pw, ph) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = RESOLUTION_PRESETS.size)
            ) {
                Text(text = "$label\n${pw}×$ph", textAlign = TextAlign.Center)
            }
        }
    }
}
