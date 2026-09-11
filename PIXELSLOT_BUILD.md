# PixelSlot prototype

Branch: `chatgpt/pixel-slot-prototype`

## v0.1 goal
A Galaxy Z Fold8-first portrait prototype focused on character reactions rather than slot complexity.

Implemented:
- Three code-drawn pixel-art girls (Momo / Luna / Mio)
- Three-number spin with sequential reel stops
- Frequent reach flow for testing presentation
- Reach cut-in
- Jackpot cut-in
- Near-miss cut-in
- Character tremble during reach
- Three-character jump celebration on jackpot
- Sparkle/confetti effect
- Win/spin counters
- Jackpot praise line: `旦那はん、すごーい！`

Current graphics are intentionally placeholder pixel art drawn in code. This lets gameplay timing and reaction direction be tested before producing final character sprite assets.

## Build
GitHub Actions builds `pixelslot/app/build/outputs/apk/debug/app-debug.apk` and uploads it as `PixelSlot-debug-apk`.
