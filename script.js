const uploadInput = document.getElementById('upload');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
const applyMosaicBtn = document.getElementById('applyMosaic');
const applyTrimBtn = document.getElementById('applyTrim');

let img = new Image();
let isImageLoaded = false;

uploadInput.addEventListener('change', (event) => {
    const file = event.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function(e) {
            img.onload = function() {
                canvas.width = img.width;
                canvas.height = img.height;
                ctx.drawImage(img, 0, 0);
                isImageLoaded = true;
            };
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
    }
});

applyMosaicBtn.addEventListener('click', () => {
    if (!isImageLoaded) return;

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const pixelData = imageData.data;
    const blockSize = 10; // モザイクのブロックサイズ

    for (let y = 0; y < canvas.height; y += blockSize) {
        for (let x = 0; x < canvas.width; x += blockSize) {
            let r = 0, g = 0, b = 0;
            let count = 0;

            // 画像のピクセルを集めて平均色を計算
            for (let dy = 0; dy < blockSize && y + dy < canvas.height; dy++) {
                for (let dx = 0; dx < blockSize && x + dx < canvas.width; dx++) {
                    const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
                    r += pixelData[idx];
                    g += pixelData[idx + 1];
                    b += pixelData[idx + 2];
                    count++;
                }
            }

            // 平均色をモザイクブロックに適用
            r = Math.floor(r / count);
            g = Math.floor(g / count);
            b = Math.floor(b / count);

            // ブロックのすべてのピクセルに平均色を適用
            for (let dy = 0; dy < blockSize && y + dy < canvas.height; dy++) {
                for (let dx = 0; dx < blockSize && x + dx < canvas.width; dx++) {
                    const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
                    pixelData[idx] = r;
                    pixelData[idx + 1] = g;
                    pixelData[idx + 2] = b;
                }
            }
        }
    }

    ctx.putImageData(imageData, 0, 0);
});

applyTrimBtn.addEventListener('click', () => {
    if (!isImageLoaded) return;

    // トリミング範囲を指定（例: 左上から50, 50の位置で100x100の範囲）
    const trimX = 50;
    const trimY = 50;
    const trimWidth = 100;
    const trimHeight = 100;

    const trimmedImage = ctx.getImageData(trimX, trimY, trimWidth, trimHeight);

    // 新しいキャンバスにトリミングした画像を描画
    canvas.width = trimWidth;
    canvas.height = trimHeight;
    ctx.putImageData(trimmedImage, 0, 0);
});
