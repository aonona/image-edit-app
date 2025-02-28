/* -----------------------------------------
   script.js

   - PC/スマホで画像をアップロード
   - Pointer Eventsでドラッグ選択(赤枠)
   - 選択部分にモザイク (デバッグ時はランダム色)
   - 選択部分をトリミング
   - Undo, Reset, Download対応
   - コンソールにデバッグログを出力
----------------------------------------- */

// 要素取得
const uploadInput = document.getElementById('upload');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

const applyMosaicBtn = document.getElementById('applyMosaic');
const applyTrimBtn = document.getElementById('applyTrim');
const downloadImageBtn = document.getElementById('downloadImage');
const undoButton = document.getElementById('undoButton');
const resetButton = document.getElementById('resetButton');

// --- 画像関連 ---
let img = new Image();
let isImageLoaded = false;

// --- 選択範囲 ---
let startX, startY, endX, endY;
let isSelecting = false;

// --- 画像データ管理 ---
let editedImageData = null;   // 最新の編集状態 (赤枠除外)
let originalImageData = null; // リセット用 (最初の状態)
let historyStack = [];        // Undo用

// --- デバッグ関連 ---
const debugMosaic = true;      // true: ブロックをランダム色で塗る, false: 画像の平均色で塗る
const blockSize = 5;           // モザイクのブロック単位 (小さいほど細かくなる)

// -------------------------------------
// 画像アップロードイベント
// -------------------------------------
uploadInput.addEventListener('change', (event) => {
    const file = event.target.files[0];
    if (!file) {
        console.warn("ファイルが選択されていません。");
        return;
    }

    const reader = new FileReader();
    reader.onload = function(e) {
        img.onload = function() {
            // キャンバスサイズを画像サイズに
            canvas.width = img.width;
            canvas.height = img.height;
            // 画像描画
            ctx.drawImage(img, 0, 0);

            isImageLoaded = true;
            console.log("画像を読み込みました:", img.width, "x", img.height);

            // 最初の状態をboth edited & originalに保存
            editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
            originalImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

            historyStack = [];
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
});

// -------------------------------------
// Pointer Events (マウス・タッチ両対応)
// -------------------------------------
canvas.addEventListener('pointerdown', (event) => {
    if (!isImageLoaded) {
        console.warn("画像がまだ読み込まれていません。");
        return;
    }
    
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width  / rect.width;
    const scaleY = canvas.height / rect.height;

    startX = (event.clientX - rect.left) * scaleX;
    startY = (event.clientY - rect.top)  * scaleY;

    isSelecting = true;
    console.log("pointerdown:", startX, startY);
});

canvas.addEventListener('pointermove', (event) => {
    if (!isSelecting) return;

    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width  / rect.width;
    const scaleY = canvas.height / rect.height;

    endX = (event.clientX - rect.left) * scaleX;
    endY = (event.clientY - rect.top)  * scaleY;

    drawSelection();
});

canvas.addEventListener('pointerup', (event) => {
    if (!isSelecting) return;

    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width  / rect.width;
    const scaleY = canvas.height / rect.height;

    endX = (event.clientX - rect.left) * scaleX;
    endY = (event.clientY - rect.top)  * scaleY;

    isSelecting = false;

    drawSelection();
    console.log("pointerup:", startX, startY, "→", endX, endY);
});

canvas.addEventListener('pointercancel', () => {
    console.warn("pointercancelが発生しました。操作が中断されました。");
    isSelecting = false;
});

// -------------------------------------
// 赤枠描画 (画面上のみ)
// -------------------------------------
function drawSelection() {
    if (!isImageLoaded) return;

    // まず編集後の状態を再描画
    ctx.putImageData(editedImageData, 0, 0);

    if (
        startX === undefined ||
        startY === undefined ||
        endX   === undefined ||
        endY   === undefined
    ) {
        return;
    }

    // 赤枠描画
    ctx.strokeStyle = 'red';
    ctx.lineWidth = 2;
    ctx.strokeRect(startX, startY, endX - startX, endY - startY);
}

// -------------------------------------
// モザイク処理
// -------------------------------------
applyMosaicBtn.addEventListener('click', () => {
    if (!isImageLoaded) {
        console.warn("モザイクボタン: 画像未読み込み。");
        return;
    }
    if (startX === undefined || endX === undefined) {
        console.warn("モザイクボタン: 選択範囲なし。");
        return;
    }

    // Undo用に保存
    historyStack.push(editedImageData);

    // 赤枠を消して元の状態に
    ctx.putImageData(editedImageData, 0, 0);

    // キャンバス全体イメージ取得
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

    // 選択範囲クランプ
    let startXInt = Math.min(startX, endX);
    let endXInt   = Math.max(startX, endX);
    let startYInt = Math.min(startY, endY);
    let endYInt   = Math.max(startY, endY);

    startXInt = Math.max(0, Math.min(canvas.width,  startXInt));
    endXInt   = Math.max(0, Math.min(canvas.width,  endXInt));
    startYInt = Math.max(0, Math.min(canvas.height, startYInt));
    endYInt   = Math.max(0, Math.min(canvas.height, endYInt));

    console.log("モザイク範囲:", {startXInt, endXInt, startYInt, endYInt});

    // 幅・高さが 0 なら何もせず終了
    if (endXInt <= startXInt || endYInt <= startYInt) {
        console.warn("モザイク範囲がゼロです。処理をスキップします。");
        return;
    }

    // ブロック単位でモザイク
    for (let y = startYInt; y < endYInt; y += blockSize) {
        for (let x = startXInt; x < endXInt; x += blockSize) {
            
            // (A) ランダム色 or (B) 平均色
            let avgR, avgG, avgB;
            if (debugMosaic) {
                // デバッグ用: ブロックごとに完全ランダム色
                avgR = Math.floor(Math.random() * 256);
                avgG = Math.floor(Math.random() * 256);
                avgB = Math.floor(Math.random() * 256);
            } else {
                // 通常: 平均色計算
                let r = 0, g = 0, b = 0, count = 0;
                for (let dy = 0; dy < blockSize && (y + dy) < endYInt; dy++) {
                    for (let dx = 0; dx < blockSize && (x + dx) < endXInt; dx++) {
                        const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
                        r += imageData.data[idx];
                        g += imageData.data[idx + 1];
                        b += imageData.data[idx + 2];
                        count++;
                    }
                }
                avgR = Math.floor(r / count);
                avgG = Math.floor(g / count);
                avgB = Math.floor(b / count);
            }

            // 塗りつぶし
            for (let dy = 0; dy < blockSize && (y + dy) < endYInt; dy++) {
                for (let dx = 0; dx < blockSize && (x + dx) < endXInt; dx++) {
                    const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
                    imageData.data[idx]     = avgR;
                    imageData.data[idx + 1] = avgG;
                    imageData.data[idx + 2] = avgB;
                }
            }
        }
    }

    // 結果を反映して editedImageData 更新
    ctx.putImageData(imageData, 0, 0);
    editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

    console.log("モザイク処理が完了しました。");
    drawSelection();
});

// -------------------------------------
// トリミング処理
// -------------------------------------
applyTrimBtn.addEventListener('click', () => {
    if (!isImageLoaded) {
        console.warn("トリミングボタン: 画像未読み込み。");
        return;
    }
    if (startX === undefined
