package com.operit.xiangqi;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋规则引擎。
 * 棋盘: 10 行 x 9 列。row 0 = 黑方底线(黑将), row 9 = 红方底线(红帅)。
 * 棋子值: 0空, 1红帅,2红仕,3红相,4红马,5红车,6红炮,7红兵,
 *        11黑将,12黑士,13黑象,14黑马,15黑车,16黑炮,17黑卒
 */
public class XiangqiEngine {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    public static final int EMPTY = 0;
    public static final int RKING = 1, RADVISOR = 2, RBISHOP = 3, RKNIGHT = 4, RROOK = 5, RCANNON = 6, RPAWN = 7;
    public static final int BKING = 11, BADVISOR = 12, BBISHOP = 13, BKNIGHT = 14, BROOK = 15, BCANNON = 16, BPAWN = 17;

    private int[] board = new int[ROWS * COLS]; // index = row*COLS + col
    private int side = 0; // 0 = 红先, 1 = 黑
    private int halfmove = 0;

    public XiangqiEngine() {
        reset();
    }

    public void reset() {
        setFen("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w");
    }

    public int pieceAt(int row, int col) {
        return board[row * COLS + col];
    }

    public int pieceAtIdx(int idx) { return board[idx]; }

    public int side() { return side; }

    public int index(int row, int col) { return row * COLS + col; }

    public static int rowOf(int idx) { return idx / COLS; }
    public static int colOf(int idx) { return idx % COLS; }

    public boolean isRedPiece(int p) { return p >= 1 && p <= 7; }
    public boolean isBlackPiece(int p) { return p >= 11 && p <= 17; }
    public boolean isSameSide(int a, int b) {
        if (a == EMPTY || b == EMPTY) return false;
        return isRedPiece(a) == isRedPiece(b);
    }

    // ---------- FEN ----------
    public void setFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        String[] ranks = parts[0].split("/");
        java.util.Arrays.fill(board, EMPTY);
        for (int r = 0; r < ROWS; r++) {
            int c = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (ch >= '1' && ch <= '9') {
                    c += (ch - '0');
                } else {
                    board[r * COLS + c] = charToPiece(ch);
                    c++;
                }
            }
        }
        if (parts.length > 1) side = parts[1].equalsIgnoreCase("b") ? 1 : 0;
        else side = 0;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            int empty = 0;
            for (int c = 0; c < COLS; c++) {
                int p = board[r * COLS + c];
                if (p == EMPTY) { empty++; }
                else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(pieceToChar(p));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < ROWS - 1) sb.append('/');
        }
        sb.append(' ').append(side == 0 ? 'w' : 'b');
        return sb.toString();
    }

    private static int charToPiece(char ch) {
        switch (ch) {
            case 'K': return RKING; case 'A': return RADVISOR; case 'E': return RBISHOP;
            case 'H': return RKNIGHT; case 'R': return RROOK; case 'C': return RCANNON; case 'P': return RPAWN;
            case 'k': return BKING; case 'a': return BADVISOR; case 'e': return BBISHOP;
            case 'h': return BKNIGHT; case 'r': return BROOK; case 'c': return BCANNON; case 'p': return BPAWN;
            default: return EMPTY;
        }
    }

    private static char pieceToChar(int p) {
        switch (p) {
            case RKING: return 'K'; case RADVISOR: return 'A'; case RBISHOP: return 'E';
            case RKNIGHT: return 'H'; case RROOK: return 'R'; case RCANNON: return 'C'; case RPAWN: return 'P';
            case BKING: return 'k'; case BADVISOR: return 'a'; case BBISHOP: return 'e';
            case BKNIGHT: return 'h'; case BROOK: return 'r'; case BCANNON: return 'c'; case BPAWN: return 'p';
            default: return ' ';
        }
    }

    public static String pieceName(int p) {
        switch (p) {
            case RKING: return "帅"; case RADVISOR: return "仕"; case RBISHOP: return "相";
            case RKNIGHT: return "马"; case RROOK: return "车"; case RCANNON: return "炮"; case RPAWN: return "兵";
            case BKING: return "将"; case BADVISOR: return "士"; case BBISHOP: return "象";
            case BKNIGHT: return "马"; case BROOK: return "车"; case BCANNON: return "炮"; case BPAWN: return "卒";
            default: return "";
        }
    }

    // ---------- 走法 ----------
    public static class Move {
        public int from, to, piece, captured;
        public Move(int from, int to, int piece, int captured) {
            this.from = from; this.to = to; this.piece = piece; this.captured = captured;
        }
        public String coordString() {
            return "(" + rowOf(from) + "," + colOf(from) + ")->(" + rowOf(to) + "," + colOf(to) + ")";
        }
    }

    /** 生成某方所有合法走法 */
    public List<Move> legalMoves(int color) { // color: 0 红, 1 黑
        List<Move> moves = new ArrayList<Move>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int p = board[r * COLS + c];
                if (p == EMPTY) continue;
                boolean red = isRedPiece(p);
                if ((color == 0 && !red) || (color == 1 && red)) continue;
                genPieceMoves(r, c, p, moves);
            }
        }
        // 过滤: 走完后己方被将军(含将帅照面)的走法非法
        List<Move> result = new ArrayList<Move>();
        for (Move m : moves) {
            int captured = board[m.to];
            board[m.to] = m.piece;
            board[m.from] = EMPTY;
            int king = color == 0 ? RKING : BKING;
            boolean safe = !isInCheck(color, king);
            board[m.from] = m.piece;
            board[m.to] = captured;
            if (safe) result.add(m);
        }
        return result;
    }

    public List<Move> legalMovesForSide() { return legalMoves(side); }

    /** 编辑模式: 直接放置棋子到某格 */
    public void setPiece(int row, int col, int piece) {
        board[row * COLS + col] = piece;
    }

    /** 返回某位置棋子的合法目标走法(仅限当前行棋方) */
    public List<Move> legalTargetsFor(int row, int col) {
        int p = board[row * COLS + col];
        if (p == EMPTY) return null;
        boolean red = isRedPiece(p);
        if ((side == 0 && !red) || (side == 1 && red)) return null;
        List<Move> all = legalMoves(side);
        List<Move> res = new ArrayList<Move>();
        int fromIdx = index(row, col);
        for (Move m : all) if (m.from == fromIdx) res.add(m);
        return res;
    }

    /** 尝试从 from 走到 to; 合法则执行并返回 true */
    public boolean tryMove(int from, int to) {
        List<Move> moves = legalMovesForSide();
        for (Move m : moves) {
            if (m.from == from && m.to == to) {
                makeMove(m);
                return true;
            }
        }
        return false;
    }

    private void genPieceMoves(int r, int c, int p, List<Move> out) {
        int idx = r * COLS + c;
        switch (p) {
            case RROOK: case BROOK: genRook(r, c, p, out); break;
            case RKNIGHT: case BKNIGHT: genKnight(r, c, p, out); break;
            case RBISHOP: case BBISHOP: genBishop(r, c, p, out); break;
            case RADVISOR: case BADVISOR: genAdvisor(r, c, p, out); break;
            case RKING: case BKING: genKing(r, c, p, out); break;
            case RCANNON: case BCANNON: genCannon(r, c, p, out); break;
            case RPAWN: case BPAWN: genPawn(r, c, p, out); break;
        }
    }

    private void addMove(int r, int c, int p, int tr, int tc, List<Move> out) {
        if (tr < 0 || tr >= ROWS || tc < 0 || tc >= COLS) return;
        int tIdx = tr * COLS + tc;
        int tp = board[tIdx];
        if (tp == EMPTY || !isSameSide(p, tp)) out.add(new Move(r * COLS + c, tIdx, p, tp));
    }

    private void genRook(int r, int c, int p, List<Move> out) {
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int tr = r + d[0], tc = c + d[1];
            while (tr >= 0 && tr < ROWS && tc >= 0 && tc < COLS) {
                int tp = board[tr * COLS + tc];
                if (tp == EMPTY) {
                    out.add(new Move(r * COLS + c, tr * COLS + tc, p, EMPTY));
                } else {
                    if (!isSameSide(p, tp)) out.add(new Move(r * COLS + c, tr * COLS + tc, p, tp));
                    break;
                }
                tr += d[0]; tc += d[1];
            }
        }
    }

    private void genKnight(int r, int c, int p, List<Move> out) {
        int[][] legs = {{1,0},{1,0},{-1,0},{-1,0},{0,1},{0,1},{0,-1},{0,-1}};
        int[][] jumps = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{-1,2},{1,-2},{-1,-2}};
        for (int i = 0; i < 8; i++) {
            int lr = r + legs[i][0], lc = c + legs[i][1];
            if (lr < 0 || lr >= ROWS || lc < 0 || lc >= COLS) continue;
            if (board[lr * COLS + lc] != EMPTY) continue; // 蹩马腿
            addMove(r, c, p, r + jumps[i][0], c + jumps[i][1], out);
        }
    }

    private void genBishop(int r, int c, int p, List<Move> out) {
        boolean red = isRedPiece(p);
        int[][] eyes = {{1,1},{1,-1},{-1,1},{-1,-1}};
        int[][] dest = {{2,2},{2,-2},{-2,2},{-2,-2}};
        for (int i = 0; i < 4; i++) {
            int er = r + eyes[i][0], ec = c + eyes[i][1];
            if (er < 0 || er >= ROWS || ec < 0 || ec >= COLS) continue;
            if (board[er * COLS + ec] != EMPTY) continue; // 塞象眼
            int tr = r + dest[i][0], tc = c + dest[i][1];
            if (tr < 0 || tr >= ROWS || tc < 0 || tc >= COLS) continue;
            // 不能过河: 红相 row>=5, 黑象 row<=4
            if (red && tr < 5) continue;
            if (!red && tr > 4) continue;
            addMove(r, c, p, tr, tc, out);
        }
    }

    private void genAdvisor(int r, int c, int p, List<Move> out) {
        boolean red = isRedPiece(p);
        for (int[] d : new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}) {
            int tr = r + d[0], tc = c + d[1];
            if (inPalace(tr, tc, red)) addMove(r, c, p, tr, tc, out);
        }
    }

    private void genKing(int r, int c, int p, List<Move> out) {
        boolean red = isRedPiece(p);
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int tr = r + d[0], tc = c + d[1];
            if (inPalace(tr, tc, red)) addMove(r, c, p, tr, tc, out);
        }
        // 将帅照面: 沿列直线吃(飞将)
        int otherKing = red ? BKING : RKING;
        int or = -1, oc = -1;
        for (int i = 0; i < ROWS * COLS; i++) {
            if (board[i] == otherKing) { or = i / COLS; oc = i % COLS; break; }
        }
        if (or >= 0 && oc == c) {
            boolean blocked = false;
            int step = r < or ? 1 : -1;
            for (int rr = r + step; rr != or; rr += step) {
                if (board[rr * COLS + c] != EMPTY) { blocked = true; break; }
            }
            if (!blocked) out.add(new Move(r * COLS + c, or * COLS + oc, p, otherKing));
        }
    }

    private void genCannon(int r, int c, int p, List<Move> out) {
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int tr = r + d[0], tc = c + d[1];
            // 先走非吃子
            while (tr >= 0 && tr < ROWS && tc >= 0 && tc < COLS) {
                if (board[tr * COLS + tc] == EMPTY) {
                    out.add(new Move(r * COLS + c, tr * COLS + tc, p, EMPTY));
                    tr += d[0]; tc += d[1];
                } else break;
            }
            // 翻山吃子
            if (tr >= 0 && tr < ROWS && tc >= 0 && tc < COLS) {
                tr += d[0]; tc += d[1];
                while (tr >= 0 && tr < ROWS && tc >= 0 && tc < COLS) {
                    int tp = board[tr * COLS + tc];
                    if (tp != EMPTY) {
                        if (!isSameSide(p, tp)) out.add(new Move(r * COLS + c, tr * COLS + tc, p, tp));
                        break;
                    }
                    tr += d[0]; tc += d[1];
                }
            }
        }
    }

    private void genPawn(int r, int c, int p, List<Move> out) {
        boolean red = isRedPiece(p);
        // 向前
        int fr = red ? r - 1 : r + 1;
        addMove(r, c, p, fr, c, out);
        // 过河后横走
        boolean crossed = red ? (r <= 4) : (r >= 5);
        if (crossed) {
            addMove(r, c, p, r, c - 1, out);
            addMove(r, c, p, r, c + 1, out);
        }
    }

    private boolean inPalace(int r, int c, boolean red) {
        if (c < 3 || c > 5) return false;
        if (red) return r >= 7 && r <= 9;
        return r >= 0 && r <= 2;
    }

    /** 判断 color 方是否正被将军 */
    public boolean isInCheck(int color) {
        int king = color == 0 ? RKING : BKING;
        return isInCheck(color, king);
    }

    private boolean isInCheck(int color, int king) {
        int kr = -1, kc = -1;
        for (int i = 0; i < ROWS * COLS; i++) {
            if (board[i] == king) { kr = i / COLS; kc = i % COLS; break; }
        }
        if (kr < 0) return false;
        // 被对方任何棋子攻击
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int p = board[r * COLS + c];
                if (p == EMPTY) continue;
                boolean enemyRed = isRedPiece(p);
                if ((color == 0 && enemyRed) || (color == 1 && !enemyRed)) continue; // 同方
                if (attacks(r, c, p, kr, kc)) return true;
            }
        }
        return false;
    }

    /** 位置 (r,c) 的棋子 p 是否能攻击到 (tr,tc) */
    private boolean attacks(int r, int c, int p, int tr, int tc) {
        int dr = tr - r, dc = tc - c;
        int adr = Math.abs(dr), adc = Math.abs(dc);
        switch (p) {
            case RROOK: case BROOK:
                if (dr != 0 && dc != 0) return false;
                return pathClear(r, c, tr, tc);
            case RKNIGHT: case BKNIGHT:
                if (!((adr == 2 && adc == 1) || (adr == 1 && adc == 2))) return false;
                // 蹩腿
                if (adr == 2) {
                    int lr = r + (dr > 0 ? 1 : -1);
                    if (board[lr * COLS + c] != EMPTY) return false;
                } else {
                    int lc = c + (dc > 0 ? 1 : -1);
                    if (board[r * COLS + lc] != EMPTY) return false;
                }
                return true;
            case RBISHOP: case BBISHOP:
                if (adr != 2 || adc != 2) return false;
                {
                    int er = r + dr / 2, ec = c + dc / 2;
                    if (board[er * COLS + ec] != EMPTY) return false;
                    boolean red = isRedPiece(p);
                    if (red && tr < 5) return false;
                    if (!red && tr > 4) return false;
                }
                return true;
            case RADVISOR: case BADVISOR:
                return adr == 1 && adc == 1 && inPalace(tr, tc, isRedPiece(p));
            case RKING: case BKING:
                if ((adr == 1 && adc == 0) || (adr == 0 && adc == 1)) return true;
                // 将帅照面
                if (dc == 0 && dr != 0) {
                    int other = isRedPiece(p) ? BKING : RKING;
                    if (board[tr * COLS + tc] != other) return false;
                    return pathClear(r, c, tr, tc);
                }
                return false;
            case RCANNON: case BCANNON:
                if (dr != 0 && dc != 0) return false;
                if (dr == 0 && dc == 0) return false;
                int count = 0;
                int stepR = dr == 0 ? 0 : (dr > 0 ? 1 : -1);
                int stepC = dc == 0 ? 0 : (dc > 0 ? 1 : -1);
                for (int rr = r + stepR, cc = c + stepC; rr != tr || cc != tc; rr += stepR, cc += stepC) {
                    if (board[rr * COLS + cc] != EMPTY) count++;
                }
                return count == 1;
            case RPAWN: case BPAWN:
                boolean red = isRedPiece(p);
                if (dc == 0) {
                    return red ? (dr == -1) : (dr == 1);
                }
                if (adc == 1 && dr == 0) {
                    return red ? (r <= 4) : (r >= 5);
                }
                return false;
        }
        return false;
    }

    private boolean pathClear(int r, int c, int tr, int tc) {
        if (r == tr) {
            int step = tc > c ? 1 : -1;
            for (int cc = c + step; cc != tc; cc += step) {
                if (board[r * COLS + cc] != EMPTY) return false;
            }
        } else {
            int step = tr > r ? 1 : -1;
            for (int rr = r + step; rr != tr; rr += step) {
                if (board[rr * COLS + c] != EMPTY) return false;
            }
        }
        return true;
    }

    /** 执行走法, 返回被吃子 */
    public int makeMove(Move m) {
        int captured = board[m.to];
        board[m.to] = m.piece;
        board[m.from] = EMPTY;
        side = 1 - side;
        halfmove++;
        return captured;
    }

    public void undoMove(Move m, int captured) {
        board[m.from] = m.piece;
        board[m.to] = captured;
        side = 1 - side;
        halfmove--;
    }

    public boolean hasLegalMoves(int color) {
        return !legalMoves(color).isEmpty();
    }

    /** 0 无, 1 将死(红输/黑输按side), 2 困毙, 3 和棋(无) */
    public int gameStatus() {
        int cur = side;
        if (hasLegalMoves(cur)) return 0;
        if (isInCheck(cur)) return 1;
        return 2;
    }

    public String statusText() {
        int st = gameStatus();
        if (st == 1) return (side == 0 ? "红方" : "黑方") + "被将死，"
                + (side == 0 ? "黑" : "红") + "方获胜！";
        if (st == 2) return (side == 0 ? "红方" : "黑方") + "无子可动（困毙），"
                + (side == 0 ? "黑" : "红") + "方获胜！";
        if (isInCheck(side)) return (side == 0 ? "红" : "黑") + "方被将军！";
        return "";
    }
}
