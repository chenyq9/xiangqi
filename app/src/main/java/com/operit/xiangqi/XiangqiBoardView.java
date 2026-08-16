package com.operit.xiangqi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * 中国象棋棋盘绘制 + 触摸交互。
 * 采用行列坐标: row 0~9 (上=黑方, 下=红方), col 0~8 (左->右)。
 * 视觉上棋盘顶部为黑方。
 */
public class XiangqiBoardView extends View {
    private XiangqiEngine engine;
    private boolean editMode = false;
    private int selectedRow = -1, selectedCol = -1; // 当前选中的棋子位置(对弈模式)
    private int palette = 0; // 编辑模式放置类型 (棋子值, 0=清除)
    private int[] legalTargets = null; // 当前选中棋子的合法目标 idx 列表

    private Paint linePaint = new Paint();
    private Paint gridPaint = new Paint();
    private Paint textPaint = new Paint();
    private Paint piecePaint = new Paint();
    private Paint pieceTextPaint = new Paint();
    private Paint highlightPaint = new Paint();
    private Paint selectedPaint = new Paint();
    private Paint lastMovePaint = new Paint();
    private int lastFrom = -1, lastTo = -1;

    private float cellW, cellH, originX, originY, pieceR;

    public interface BoardListener {
        void onBoardChanged(String fen, String status);
        void onMoveMade(int from, int to);
    }
    private BoardListener listener;

    public XiangqiBoardView(Context context, XiangqiEngine eng) {
        super(context);
        this.engine = eng;
        init();
    }

    private void init() {
        linePaint.setColor(0xFF6B3A1F);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setAntiAlias(true);

        gridPaint.setColor(0xFFE8D5B5);
        gridPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFF6B3A1F);
        textPaint.setTextSize(28f);
        textPaint.setAntiAlias(true);

        piecePaint.setAntiAlias(true);
        pieceTextPaint.setAntiAlias(true);
        pieceTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        highlightPaint.setColor(0x3355AAFF);
        selectedPaint.setColor(0x55FFAA00);
        lastMovePaint.setColor(0x44A0522D);
    }

    public void setListener(BoardListener l) { this.listener = l; }
    public void setEngine(XiangqiEngine e) { this.engine = e; invalidate(); }
    public void setEditMode(boolean edit) { this.editMode = edit; invalidate(); }
    public void setPalette(int p) { this.palette = p; invalidate(); }
    public void setSelected(int r, int c) { this.selectedRow = r; this.selectedCol = c; invalidate(); }
    public void setLastMove(int f, int t) { this.lastFrom = f; this.lastTo = t; invalidate(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = pieceR = w * 0.045f;
        float usable = w - pad * 2;
        cellW = usable / (XiangqiEngine.COLS - 1);
        cellH = usable / (XiangqiEngine.ROWS - 1);
        originX = pad;
        originY = pad;
        pieceR = cellW * 0.42f;
    }

    private float cx(int col) { return originX + col * cellW; }
    private float cy(int row) { return originY + row * cellH; }
    private float cx(float col) { return originX + col * cellW; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        drawGrid(canvas);
        drawPieces(canvas);
        drawHighlights(canvas);
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawColor(0xFFE8D5B5);
    }

    private void drawGrid(Canvas canvas) {
        // 横线 10 条
        for (int r = 0; r < XiangqiEngine.ROWS; r++) {
            canvas.drawLine(cx(0), cy(r), cx(XiangqiEngine.COLS - 1), cy(r), linePaint);
        }
        // 竖线 9 条
        for (int c = 0; c < XiangqiEngine.COLS; c++) {
            canvas.drawLine(cx(c), cy(0), cx(c), cy(XiangqiEngine.ROWS - 1), linePaint);
        }
        // 河界断开横线(第4、5行之间无竖线)
        linePaint.setColor(0xFFE8D5B5); // 擦掉中间竖线
        for (int c = 1; c < XiangqiEngine.COLS - 1; c++) {
            canvas.drawLine(cx(c), cy(4), cx(c), cy(5), linePaint);
        }
        linePaint.setColor(0xFF6B3A1F);
        // 九宫斜线
        canvas.drawLine(cx(3), cy(0), cx(5), cy(2), linePaint);
        canvas.drawLine(cx(5), cy(0), cx(3), cy(2), linePaint);
        canvas.drawLine(cx(3), cy(9), cx(5), cy(7), linePaint);
        canvas.drawLine(cx(5), cy(9), cx(3), cy(7), linePaint);
        // 炮位标记
        drawStar(canvas, 1, 2); drawStar(canvas, 7, 2);
        drawStar(canvas, 1, 7); drawStar(canvas, 7, 7);
        // 兵位标记
        for (int c = 0; c < 9; c += 2) {
            drawStar(canvas, 3, c);
            drawStar(canvas, 6, c);
        }
        // 河界文字
        textPaint.setTextSize(cellH * 0.6f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = (cy(4) + cy(5)) / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("楚 河", cx(2.2f), textY, textPaint);
        canvas.drawText("汉 界", cx(6.8f), textY, textPaint);
    }

    private void drawStar(Canvas canvas, int row, int col) {
        float x = cx(col), y = cy(row);
        float r = cellW * 0.08f;
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i + Math.PI / 4;
            canvas.drawLine(x - r, y - r, x + r, y + r, linePaint);
            canvas.drawLine(x + r, y - r, x - r, y + r, linePaint);
            canvas.drawLine(x, y, x + r * (float) Math.cos(angle), y + r * (float) Math.sin(angle), linePaint);
        }
    }

    private void drawPieces(Canvas canvas) {
        for (int r = 0; r < XiangqiEngine.ROWS; r++) {
            for (int c = 0; c < XiangqiEngine.COLS; c++) {
                int p = engine.pieceAt(r, c);
                if (p != XiangqiEngine.EMPTY) drawPiece(canvas, r, c, p);
            }
        }
    }

    private void drawPiece(Canvas canvas, int row, int col, int piece) {
        float x = cx(col), y = cy(row);
        boolean red = engine.isRedPiece(piece);
        piecePaint.setStyle(Paint.Style.FILL);
        piecePaint.setColor(red ? 0xFFD2691E : 0xFF2F2F2F);
        canvas.drawCircle(x, y, pieceR, piecePaint);
        piecePaint.setStyle(Paint.Style.STROKE);
        piecePaint.setStrokeWidth(2f);
        piecePaint.setColor(red ? 0xFF8B4513 : 0xFF111111);
        canvas.drawCircle(x, y, pieceR, piecePaint);
        pieceTextPaint.setTextSize(pieceR * 1.25f);
        pieceTextPaint.setTextAlign(Paint.Align.CENTER);
        pieceTextPaint.setColor(red ? 0xFFFFE4B5 : 0xFFFFF8DC);
        Paint.FontMetrics fm = pieceTextPaint.getFontMetrics();
        float ty = y - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(XiangqiEngine.pieceName(piece), x, ty, pieceTextPaint);
    }

    private void drawHighlights(Canvas canvas) {
        if (selectedRow >= 0 && selectedCol >= 0) {
            float x = cx(selectedCol), y = cy(selectedRow);
            canvas.drawCircle(x, y, pieceR * 1.25f, selectedPaint);
        }
        if (lastFrom >= 0 && lastTo >= 0) {
            int fr = lastFrom / XiangqiEngine.COLS, fc = lastFrom % XiangqiEngine.COLS;
            int tr = lastTo / XiangqiEngine.COLS, tc = lastTo % XiangqiEngine.COLS;
            canvas.drawCircle(cx(fc), cy(fr), pieceR * 1.2f, lastMovePaint);
            canvas.drawCircle(cx(tc), cy(tr), pieceR * 1.2f, lastMovePaint);
        }
        if (legalTargets != null) {
            for (int idx : legalTargets) {
                int r = idx / XiangqiEngine.COLS, c = idx % XiangqiEngine.COLS;
                float x = cx(c), y = cy(r);
                if (engine.pieceAt(r, c) == XiangqiEngine.EMPTY) {
                    canvas.drawCircle(x, y, pieceR * 0.35f, highlightPaint);
                } else {
                    canvas.drawCircle(x, y, pieceR * 1.2f, highlightPaint);
                }
            }
        }
    }

    public void setLegalTargets(int[] targets) { this.legalTargets = targets; invalidate(); }

    // ---------- 触摸 ----------
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() != android.view.MotionEvent.ACTION_DOWN) return true;
        int col = Math.round((event.getX() - originX) / cellW);
        int row = Math.round((event.getY() - originY) / cellH);
        if (col < 0 || col >= XiangqiEngine.COLS || row < 0 || row >= XiangqiEngine.ROWS) return true;
        handleTap(row, col);
        return true;
    }

    private void handleTap(int row, int col) {
        if (editMode) {
            // 编辑模式: 根据 palette 放置/清除 (palette 直接是棋子值或EMPTY)
            engine.setPiece(row, col, palette);
            invalidate();
            notifyChanged();
            return;
        }
        // 对弈模式
        int piece = engine.pieceAt(row, col);
        if (selectedRow < 0) {
            // 选择己方棋子
            if (piece != XiangqiEngine.EMPTY && isOwnPiece(piece)) {
                selectedRow = row; selectedCol = col;
                legalTargets = toTargets(engine.legalTargetsFor(row, col));
                invalidate();
            }
        } else {
            // 已选中: 若点己方另一子则切换选中, 否则尝试走子
            if (piece != XiangqiEngine.EMPTY && isOwnPiece(piece)) {
                selectedRow = row; selectedCol = col;
                legalTargets = toTargets(engine.legalTargetsFor(row, col));
                invalidate();
            } else {
                int from = engine.index(selectedRow, selectedCol);
                int to = engine.index(row, col);
                if (engine.tryMove(from, to)) {
                    lastFrom = from; lastTo = to;
                    clearSelection();
                    notifyChanged();
                } else {
                    // 非法走法
                    clearSelection();
                    invalidate();
                }
            }
        }
    }

    private boolean isOwnPiece(int p) {
        return engine.side() == 0 ? engine.isRedPiece(p) : engine.isBlackPiece(p);
    }

    private int[] toTargets(java.util.List<XiangqiEngine.Move> moves) {
        if (moves == null) return null;
        int[] arr = new int[moves.size()];
        for (int i = 0; i < moves.size(); i++) arr[i] = moves.get(i).to;
        return arr;
    }

    private void clearSelection() {
        selectedRow = -1; selectedCol = -1;
        legalTargets = null;
        invalidate();
    }

    private void notifyChanged() {
        if (listener != null) listener.onBoardChanged(engine.toFen(), engine.statusText());
    }
}