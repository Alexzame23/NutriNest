param(
    [string]$Source = (Join-Path $PSScriptRoot '..\app\src\main\res\drawable-nodpi\nutrinest_logo.png'),
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resolvedSource = (Resolve-Path -LiteralPath $Source).Path
$resolvedResourceRoot = (Resolve-Path -LiteralPath $ResourceRoot).Path
$logo = [System.Drawing.Image]::FromFile($resolvedSource)

function New-Canvas([int]$size) {
    $bitmap = [System.Drawing.Bitmap]::new(
        $size,
        $size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $bitmap.SetResolution(96, 96)
    return $bitmap
}

function Set-HighQuality([System.Drawing.Graphics]$graphics) {
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
}

function Draw-CenteredLogo(
    [System.Drawing.Graphics]$graphics,
    [int]$canvasSize,
    [double]$scale
) {
    $logoSize = [int][Math]::Round($canvasSize * $scale)
    $offset = [int][Math]::Round(($canvasSize - $logoSize) / 2)
    $destination = [System.Drawing.Rectangle]::new($offset, $offset, $logoSize, $logoSize)
    $graphics.DrawImage($logo, $destination)
}

function Save-Png([System.Drawing.Bitmap]$bitmap, [string]$path) {
    $directory = Split-Path -Parent $path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Add-RoundedRectangle(
    [System.Drawing.Drawing2D.GraphicsPath]$path,
    [System.Drawing.RectangleF]$rectangle,
    [float]$radius
) {
    $diameter = $radius * 2
    $arc = [System.Drawing.RectangleF]::new($rectangle.X, $rectangle.Y, $diameter, $diameter)
    $path.AddArc($arc, 180, 90)
    $arc.X = $rectangle.Right - $diameter
    $path.AddArc($arc, 270, 90)
    $arc.Y = $rectangle.Bottom - $diameter
    $path.AddArc($arc, 0, 90)
    $arc.X = $rectangle.Left
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()
}

try {
    # Android adaptive icons use a 108 dp canvas. Keeping the visible artwork
    # inside the central ~66 dp safe zone prevents leaf and bowl clipping under
    # circular, squircle, rounded-square, and OEM launcher masks.
    $adaptiveSize = 432
    $foreground = New-Canvas $adaptiveSize
    $foregroundGraphics = [System.Drawing.Graphics]::FromImage($foreground)
    try {
        Set-HighQuality $foregroundGraphics
        $foregroundGraphics.Clear([System.Drawing.Color]::Transparent)
        Draw-CenteredLogo $foregroundGraphics $adaptiveSize 0.70
    } finally {
        $foregroundGraphics.Dispose()
    }

    $foregroundPath = Join-Path $resolvedResourceRoot 'drawable-nodpi\nutrinest_launcher_foreground.png'
    Save-Png $foreground $foregroundPath

    # Android 13 themed icons consume a single-color alpha mask. Reuse the
    # official mark's exact silhouette and holes, replacing only its RGB color.
    $monochrome = New-Canvas $adaptiveSize
    $monochromeGraphics = [System.Drawing.Graphics]::FromImage($monochrome)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        Set-HighQuality $monochromeGraphics
        $monochromeGraphics.Clear([System.Drawing.Color]::Transparent)
        $matrix = [System.Drawing.Imaging.ColorMatrix]::new()
        $matrix.Matrix00 = 0
        $matrix.Matrix11 = 0
        $matrix.Matrix22 = 0
        $matrix.Matrix40 = 1
        $matrix.Matrix41 = 1
        $matrix.Matrix42 = 1
        $attributes.SetColorMatrix($matrix)
        $rectangle = [System.Drawing.Rectangle]::new(0, 0, $adaptiveSize, $adaptiveSize)
        $monochromeGraphics.DrawImage(
            $foreground,
            $rectangle,
            0,
            0,
            $adaptiveSize,
            $adaptiveSize,
            [System.Drawing.GraphicsUnit]::Pixel,
            $attributes
        )
    } finally {
        $attributes.Dispose()
        $monochromeGraphics.Dispose()
    }

    $monochromePath = Join-Path $resolvedResourceRoot 'drawable-nodpi\nutrinest_launcher_monochrome.png'
    Save-Png $monochrome $monochromePath

    # A default bitmap keeps the manifest resource resolvable to Android's
    # linker; every supported device (minSdk 26) selects the adaptive v26
    # version at runtime. It also provides a useful source for tooling that
    # previews the base resource without applying Android's adaptive mask.
    $legacySize = 512
    $background = [System.Drawing.ColorTranslator]::FromHtml('#FAF3E7')
    foreach ($round in $false, $true) {
        $legacy = New-Canvas $legacySize
        $graphics = [System.Drawing.Graphics]::FromImage($legacy)
        $brush = [System.Drawing.SolidBrush]::new($background)
        try {
            Set-HighQuality $graphics
            $graphics.Clear([System.Drawing.Color]::Transparent)
            if ($round) {
                $graphics.FillEllipse($brush, 0, 0, $legacySize, $legacySize)
            } else {
                $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
                try {
                    Add-RoundedRectangle $path ([System.Drawing.RectangleF]::new(0, 0, $legacySize, $legacySize)) ($legacySize * 0.22)
                    $graphics.FillPath($brush, $path)
                } finally {
                    $path.Dispose()
                }
            }
            Draw-CenteredLogo $graphics $legacySize 0.72
        } finally {
            $brush.Dispose()
            $graphics.Dispose()
        }

        $suffix = if ($round) { '_round' } else { '' }
        $legacyPath = Join-Path $resolvedResourceRoot "mipmap-anydpi\nutrinest_launcher$suffix.png"
        Save-Png $legacy $legacyPath
        $legacy.Dispose()
    }

    $foreground.Dispose()
    $monochrome.Dispose()
} finally {
    $logo.Dispose()
}
