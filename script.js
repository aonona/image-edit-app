/* =================================
   script.js (デバッグ用 - 真っ赤モザイク)
   ================================= */
// Canvas2Dコンテキストの取得 (willReadFrequentlyで警告を抑制)
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d', { willReadFrequently: true });

// 要素取得
const uploadInput = document.getElementById('upload');
const applyMosaicBtn = document.getElementById('applyMosaic');
const applyTrimBtn = document.getElementById('applyTrim');
const downloadImageBtn = document.getElementById('downloadImage');
const undoButton = document.getElementById('undoButton');
const resetButton = document.getElementById('resetButton');

let img = new Image();
let isImageLoaded = false;

// 選択範囲
let startX, startY, endX, endY;
let isSelecting = false;

// 画像データ管理
let editedImageData = null;
let originalImageData = null;
let historyStack = [];

// デバッグ用: 真っ赤に塗りつぶす
const blockSize = 10;

// -------------- アップロード --------------
uploadInput.addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) {
    console.warn("ファイルが選択されていません。");
    return;
  }

  const reader = new FileReader();
  reader.onload = (ev) => {
    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);

      isImageLoaded = true;
      console.log("画像サイズ:", canvas.width, "x", canvas.height);

      editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      originalImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      historyStack = [];

      console.log("画像を読み込みました。");
    };
    img.src = ev.target.result;
  };
  reader.readAsDataURL(file);
});

// -------------- Pointer Events --------------
canvas.addEventListener('pointerdown', (event) => {
  if (!isImageLoaded) {
    console.warn("pointerdown: 画像未読み込み");
    return;
  }

  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;

  startX = (event.clientX - rect.left) * scaleX;
  startY = (event.clientY - rect.top)  * scaleY;

  isSelecting = true;
  console.log("pointerdown:", startX, startY);
});

canvas.addEventListener('pointermove', (event) => {
  if (!isSelecting) return;

  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;

  endX = (event.clientX - rect.left) * scaleX;
  endY = (event.clientY - rect.top)  * scaleY;

  drawSelection();
});

canvas.addEventListener('pointerup', (event) => {
  if (!isSelecting) return;

  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;

  endX = (event.clientX - rect.left) * scaleX;
  endY = (event.clientY - rect.top)  * scaleY;

  isSelecting = false;
  drawSelection();
  console.log("pointerup:", startX, startY, "→", endX, endY);
});

canvas.addEventListener('pointercancel', () => {
  console.warn("pointercancelが発生。指がキャンバス外に出た等の可能性あり。");
  isSelecting = false;
});

// -------------- 赤枠描画 --------------
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

// -------------- 真っ赤モザイク --------------
applyMosaicBtn.addEventListener('click', () => {
  if (!isImageLoaded) {
    console.warn("モザイク: 画像未読み込み");
    return;
  }
  if (startX === undefined || endX === undefined) {
    console.warn("モザイク: 選択範囲がありません。");
    return;
  }

  // Undo用に保存
  historyStack.push(editedImageData);

  // 赤枠を消して元の状態に
  ctx.putImageData(editedImageData, 0, 0);

  // 画像データ取得
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  let startXInt = Math.min(startX, endX);
  let endXInt   = Math.max(startX, endX);
  let startYInt = Math.min(startY, endY);
  let endYInt   = Math.max(startY, endY);

  // クランプ
  startXInt = Math.max(0, Math.min(canvas.width,  startXInt));
  endXInt   = Math.max(0, Math.min(canvas.width,  endXInt));
  startYInt = Math.max(0, Math.min(canvas.height, startYInt));
  endYInt   = Math.max(0, Math.min(canvas.height, endYInt));

  console.log("モザイク範囲:", {startXInt, endXInt, startYInt, endYInt});

  // 幅or高さが0なら終了
  if (endXInt <= startXInt || endYInt <= startYInt) {
    console.warn("モザイク範囲がゼロ。何もしません。");
    return;
  }

  // ブロックごとに真っ赤
  for (let y = startYInt; y < endYInt; y += blockSize) {
    for (let x = startXInt; x < endXInt; x += blockSize) {
      for (let dy = 0; dy < blockSize && y + dy < endYInt; dy++) {
        for (let dx = 0; dx < blockSize && x + dx < endXInt; dx++) {
          const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
          imageData.data[idx]     = 255; // R
          imageData.data[idx + 1] = 0;   // G
          imageData.data[idx + 2] = 0;   // B
        }
      }
    }
  }

  ctx.putImageData(imageData, 0, 0);
  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  console.log("真っ赤モザイクをかけました。");
  drawSelection();
});

// -------------- トリミング --------------
applyTrimBtn.addEventListener('click', () => {
  if (!isImageLoaded) {
    console.warn("トリミング: 画像未読み込み");
    return;
  }
  if (
    startX === undefined || startY === undefined ||
    endX   === undefined || endY   === undefined
  ) {
    console.warn("トリミング: 選択範囲がありません。");
    return;
  }

  historyStack.push(editedImageData);
  ctx.putImageData(editedImageData, 0, 0);

  let trimX = Math.min(startX, endX);
  let trimY = Math.min(startY, endY);
  let trimWidth  = Math.abs(endX - startX);
  let trimHeight = Math.abs(endY - startY);

  trimX = Math.max(0, Math.min(canvas.width, trimX));
  trimY = Math.max(0, Math.min(canvas.height, trimY));
  if (trimX + trimWidth  > canvas.width)  trimWidth  = canvas.width  - trimX;
  if (trimY + trimHeight > canvas.height) trimHeight = canvas.height - trimY;

  console.log("トリミング範囲:", {trimX, trimY, trimWidth, trimHeight});

  const trimmedData = ctx.getImageData(trimX, trimY, trimWidth, trimHeight);
  canvas.width = trimWidth;
  canvas.height = trimHeight;
  ctx.putImageData(trimmedData, 0, 0);

  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  console.log("トリミングしました。");

  startX = startY = endX = endY = undefined;
});

// -------------- ダウンロード --------------
downloadImageBtn.addEventListener('click', () => {
  if (!isImageLoaded) {
    console.warn("ダウンロード: 画像未読み込み");
    return;
  }
  ctx.putImageData(editedImageData, 0, 0);

  const link = document.createElement('a');
  link.download = 'edited-image.png';
  link.href = canvas.toDataURL();
  link.click();

  console.log("ダウンロードしました。");
});

// -------------- Undo --------------
undoButton.addEventListener('click', () => {
  if (historyStack.length === 0) {
    console.warn("Undo: 履歴なし");
    return;
  }
  editedImageData = historyStack.pop();
  canvas.width = editedImageData.width;
  canvas.height = editedImageData.height;
  ctx.putImageData(editedImageData, 0, 0);

  startX = startY = endX = endY = undefined;
  console.log("一個前に戻しました。");
});

// -------------- リセット --------------
resetButton.addEventListener('click', () => {
  if (!originalImageData) {
    console.warn("リセット: オリジナルがありません");
    return;
  }

  editedImageData = originalImageData;
  canvas.width = originalImageData.width;
  canvas.height = originalImageData.height;
  ctx.putImageData(originalImageData, 0, 0);

  historyStack = [];
  startX = startY = endX = endY = undefined;
  console.log("リセット完了。初期状態に戻しました。");
});
