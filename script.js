/* script.js */

const uploadInput = document.getElementById('upload');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

const applyMosaicBtn = document.getElementById('applyMosaic');
const applyTrimBtn = document.getElementById('applyTrim');
const downloadImageBtn = document.getElementById('downloadImage');
const undoButton = document.getElementById('undoButton');
const resetButton = document.getElementById('resetButton');

// 画像関連
let img = new Image();
let isImageLoaded = false;

// 選択範囲
let startX, startY, endX, endY;
let isSelecting = false;

// 編集後の状態 (赤枠除外)
let editedImageData = null;
let originalImageData = null;
let historyStack = [];

// ---------------------------
// 画像読み込み
// ---------------------------
uploadInput.addEventListener('change', (event) => {
  const file = event.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = function(e) {
    img.onload = function() {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);

      isImageLoaded = true;
      editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      originalImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      historyStack = [];
    };
    img.src = e.target.result;
  };
  reader.readAsDataURL(file);
});

// ---------------------------
// Pointer Events
// ---------------------------
canvas.addEventListener('pointerdown', (event) => {
  if (!isImageLoaded) return;
  
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width  / rect.width;
  const scaleY = canvas.height / rect.height;

  startX = (event.clientX - rect.left) * scaleX;
  startY = (event.clientY - rect.top)  * scaleY;

  isSelecting = true;
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
});

canvas.addEventListener('pointercancel', () => {
  isSelecting = false;
});

// ---------------------------
// 赤枠表示
// ---------------------------
function drawSelection() {
  if (!isImageLoaded) return;

  ctx.putImageData(editedImageData, 0, 0);

  if (
    startX === undefined ||
    startY === undefined ||
    endX   === undefined ||
    endY   === undefined
  ) {
    return;
  }

  ctx.strokeStyle = 'red';
  ctx.lineWidth = 2;
  ctx.strokeRect(startX, startY, endX - startX, endY - startY);
}

// ---------------------------
// モザイク処理
// ---------------------------
applyMosaicBtn.addEventListener('click', () => {
  if (!isImageLoaded || startX === undefined || endX === undefined) {
    console.log("画像未読み込み、または選択範囲が確定していません。");
    return;
  }

  // Undo用に今の状態を保存
  historyStack.push(editedImageData);

  // 赤枠を消して元の状態に戻す
  ctx.putImageData(editedImageData, 0, 0);

  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const blockSize = 10;

  // 選択範囲の確定 (クランプあり)
  let startXInt = Math.min(startX, endX);
  let endXInt   = Math.max(startX, endX);
  let startYInt = Math.min(startY, endY);
  let endYInt   = Math.max(startY, endY);

  // キャンバス外に行かないように調整
  startXInt = Math.max(0, Math.min(canvas.width,  startXInt));
  endXInt   = Math.max(0, Math.min(canvas.width,  endXInt));
  startYInt = Math.max(0, Math.min(canvas.height, startYInt));
  endYInt   = Math.max(0, Math.min(canvas.height, endYInt));

  // デバッグ用: 選択範囲を出力
  console.log("Mosaic range:",
    { startXInt, endXInt, startYInt, endYInt }
  );

  // (幅や高さが0以下なら何もしない)
  if (endXInt <= startXInt || endYInt <= startYInt) {
    console.log("選択範囲が無効です。");
    return;
  }

  // ブロック単位でモザイク
  for (let y = startYInt; y < endYInt; y += blockSize) {
    for (let x = startXInt; x < endXInt; x += blockSize) {
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

      // 平均値を計算
      const avgR = Math.floor(r / count);
      const avgG = Math.floor(g / count);
      const avgB = Math.floor(b / count);

      // ブロック内を平均色で塗りつぶし
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

  // 結果反映
  ctx.putImageData(imageData, 0, 0);
  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  // 必要があれば赤枠を再表示
  drawSelection();
});

// ---------------------------
// トリミング処理
// ---------------------------
applyTrimBtn.addEventListener('click', () => {
  if (!isImageLoaded ||
      startX === undefined || startY === undefined ||
      endX   === undefined || endY   === undefined
  ) {
    console.log("画像未読み込み、または選択範囲が確定していません(Trim)。");
    return;
  }

  // 履歴保存
  historyStack.push(editedImageData);

  // 赤枠を消して元の状態に戻す
  ctx.putImageData(editedImageData, 0, 0);

  // トリミング範囲をクランプ
  let trimX = Math.min(startX, endX);
  let trimY = Math.min(startY, endY);
  let trimWidth  = Math.abs(endX - startX);
  let trimHeight = Math.abs(endY - startY);

  trimX = Math.max(0, Math.min(canvas.width,  trimX));
  trimY = Math.max(0, Math.min(canvas.height, trimY));

  if (trimX + trimWidth  > canvas.width)  trimWidth  = canvas.width  - trimX;
  if (trimY + trimHeight > canvas.height) trimHeight = canvas.height - trimY;

  const trimmedData = ctx.getImageData(trimX, trimY, trimWidth, trimHeight);

  // キャンバスをトリミング後サイズに変えて描画
  canvas.width = trimWidth;
  canvas.height = trimHeight;
  ctx.putImageData(trimmedData, 0, 0);

  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  // 選択範囲リセット
  startX = startY = endX = endY = undefined;
});

// ---------------------------
// ダウンロード処理
// ---------------------------
downloadImageBtn.addEventListener('click', () => {
  if (!isImageLoaded) return;

  ctx.putImageData(editedImageData, 0, 0);

  const link = document.createElement('a');
  link.download = 'edited-image.png';
  link.href = canvas.toDataURL();
  link.click();
});

// ---------------------------
// Undo処理
// ---------------------------
undoButton.addEventListener('click', () => {
  if (historyStack.length === 0) return;

  editedImageData = historyStack.pop();
  canvas.width = editedImageData.width;
  canvas.height = editedImageData.height;
  ctx.putImageData(editedImageData, 0, 0);

  startX = startY = endX = endY = undefined;
});

// ---------------------------
// リセット処理
// ---------------------------
resetButton.addEventListener('click', () => {
  if (!originalImageData) return;

  editedImageData = originalImageData;
  canvas.width = originalImageData.width;
  canvas.height = originalImageData.height;
  ctx.putImageData(originalImageData, 0, 0);

  historyStack = [];
  startX = startY = endX = endY = undefined;
});
