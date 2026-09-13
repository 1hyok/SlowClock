#!/usr/bin/env bash
# Export opaque CI-rendered screenshots as Play's 24-bit PNG without changing any RGB pixel.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" != "" && "${1:-}" != "--check" ]]; then
  echo 'Usage: scripts/export-play-screenshots.sh [--check]' >&2
  exit 2
fi
export PLAY_SCREENSHOT_CHECK="${1:-}"
jshell -J-Djava.awt.headless=true --execution local --feedback silent - <<'JAVA'
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
try {
    var sourceRoot = Path.of("feature/main/src/screenshotTestDebug/reference/com/example/slowclock/ui/StoreScreenshotTestKt");
    var names = Map.of("StoreMainScreenshot_", "01-main.png", "StoreDoneScreenshot_", "02-done.png", "StoreTimelineScreenshot_", "03-timeline.png", "StoreDarkScreenshot_", "04-dark.png");
    if (!Files.isDirectory(sourceRoot)) throw new IllegalStateException("No CI baseline yet. Put the screenshot-baseline label on the PR and pull the commit CI makes, then run this again: " + sourceRoot);
    for (var entry : names.entrySet()) {
        List<Path> candidates;
        try (var paths = Files.list(sourceRoot)) {
            candidates = paths.filter(p -> p.getFileName().toString().startsWith(entry.getKey())).toList();
        }
        if (candidates.size() != 1) throw new IllegalStateException("Expected one current CI baseline for " + entry.getKey());
        var source = ImageIO.read(candidates.get(0).toFile());
        if (source.getWidth() != 1080 || source.getHeight() != 1920) throw new IllegalStateException("Expected 1080x1920 CI baseline: " + candidates.get(0));
        var pixels = source.getRGB(0, 0, 1080, 1920, null, 0, 1080);
        if (Arrays.stream(pixels).anyMatch(p -> (p >>> 24) != 255)) throw new IllegalStateException("Transparent source pixel: " + candidates.get(0));
        var target = Path.of("docs/play/screenshots", entry.getValue());
        if (!"--check".equals(System.getenv("PLAY_SCREENSHOT_CHECK"))) {
            var rgb = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
            rgb.setRGB(0, 0, 1080, 1920, pixels, 0, 1080);
            if (!ImageIO.write(rgb, "png", target.toFile())) throw new IllegalStateException("PNG writer unavailable");
        }
        var exported = ImageIO.read(target.toFile());
        if (exported.getWidth() != 1080 || exported.getHeight() != 1920 || exported.getColorModel().hasAlpha() || exported.getColorModel().getPixelSize() != 24) throw new IllegalStateException("Invalid 24-bit Play export: " + target);
        if (!Arrays.equals(pixels, exported.getRGB(0, 0, 1080, 1920, null, 0, 1080))) throw new IllegalStateException("RGB pixels differ from CI baseline: " + target);
        System.out.println(target + ": 1080x1920, opaque RGB24, all RGB pixels identical to CI baseline");
    }
} catch (Exception error) {
    error.printStackTrace();
    System.exit(1);
}
System.exit(0);
JAVA
