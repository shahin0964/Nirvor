package com.nirvor.app.ui.tools

import androidx.compose.runtime.Composable
import com.nirvor.app.ar.ArMode
import com.nirvor.app.tools.ToolId
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.tools.ar.ArMeasureTool
import com.nirvor.app.ui.tools.camera.ColorDetectorTool
import com.nirvor.app.ui.tools.camera.DocScannerTool
import com.nirvor.app.ui.tools.camera.MagnifierTool
import com.nirvor.app.ui.tools.camera.ObjectsTool
import com.nirvor.app.ui.tools.camera.OcrTool
import com.nirvor.app.ui.tools.camera.QrTool
import com.nirvor.app.ui.tools.camera.ReceiptTool
import com.nirvor.app.ui.tools.office.ExcelToPdfTool
import com.nirvor.app.ui.tools.office.ImageToPdfTool
import com.nirvor.app.ui.tools.office.PdfMergeSplitTool
import com.nirvor.app.ui.tools.office.PdfToExcelTool
import com.nirvor.app.ui.tools.office.PdfToWordTool
import com.nirvor.app.ui.tools.office.WordTool
import com.nirvor.app.ui.tools.office.WordToPdfTool

/** Opens one tool of the Tool Box. */
@Composable
fun ToolHost(tool: ToolId, vm: MainViewModel, onBack: () -> Unit, onOpenTool: (ToolId) -> Unit) {
    when (tool) {
        ToolId.Level -> LevelTool(onBack)
        ToolId.Compass -> CompassTool(onBack)
        ToolId.Angle -> AngleTool(onBack)
        ToolId.Tilt -> TiltTool(onBack)
        ToolId.Stopwatch -> StopwatchTool(onBack)
        ToolId.Flashlight -> FlashlightTool(onBack)
        ToolId.SoundMeter -> SoundMeterTool(onBack)
        ToolId.LightMeter -> LightMeterTool(onBack)
        ToolId.Ruler -> RulerTool(onBack)
        ToolId.ArDistance -> ArMeasureTool(ArMode.Distance, onBack)
        ToolId.ArAngle -> ArMeasureTool(ArMode.Angle, onBack)
        ToolId.ArArea -> ArMeasureTool(ArMode.Area, onBack)
        ToolId.ArHeight -> ArMeasureTool(ArMode.Height, onBack)
        ToolId.ArPoints -> ArMeasureTool(ArMode.Points, onBack)
        ToolId.ArWall -> ArMeasureTool(ArMode.Wall, onBack)
        ToolId.Magnifier -> MagnifierTool(onBack)
        ToolId.ColorDetector -> ColorDetectorTool(onBack)
        ToolId.DocScanner -> DocScannerTool(onBack)
        ToolId.Ocr -> OcrTool(onBack)
        ToolId.Receipt -> ReceiptTool(vm, onBack)
        ToolId.QrCode -> QrTool(onBack)
        ToolId.Objects -> ObjectsTool(onBack)
        ToolId.Calculator -> CalculatorTool(onBack)
        ToolId.Units -> UnitConverterTool(onBack)
        ToolId.Bmi -> BmiTool(onBack)
        ToolId.Age -> AgeTool(onBack)
        ToolId.Percent -> PercentTool(onBack)
        ToolId.Discount -> DiscountTool(onBack)
        ToolId.Emi -> EmiTool(vm, onBack)
        ToolId.Word -> WordTool(onBack)
        ToolId.PdfToWord -> PdfToWordTool(onBack, onOpenWord = { onOpenTool(ToolId.Word) })
        ToolId.PdfToExcel -> PdfToExcelTool(onBack)
        ToolId.WordToPdf -> WordToPdfTool(onBack)
        ToolId.ExcelToPdf -> ExcelToPdfTool(onBack)
        ToolId.ImageToPdf -> ImageToPdfTool(onBack)
        ToolId.PdfMergeSplit -> PdfMergeSplitTool(onBack)
    }
}
