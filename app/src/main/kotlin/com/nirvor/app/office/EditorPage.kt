package com.nirvor.app.office

/**
 * The Word editor's page: a white A4-like sheet you type on (the browser's own rich-text
 * editing), plus a small script the app talks to — commands in, document state and changes out.
 * The script never contains the dollar sign, so it can live in a Kotlin string.
 */
object EditorPage {
    const val HTML = """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
html, body { margin: 0; background: #e9eef2; -webkit-text-size-adjust: 100%; }
body { padding: 12px 10px 45vh; }
#page { background: #fff; margin: 0 auto; max-width: 820px; min-height: 128vw; box-sizing: border-box;
  padding: 34px 26px 40px; border-radius: 6px; box-shadow: 0 2px 12px rgba(30, 50, 70, .13); }
#ed { outline: none; position: relative; min-height: 70vh; color: #1b1f2a; caret-color: #0ba765;
  font: 16px/1.55 sans-serif; white-space: pre-wrap; overflow-wrap: break-word; word-break: break-word; }
#ed.blank p:first-child::before { content: 'এখানে লিখতে শুরু করুন…'; color: #9aa6b4; position: absolute; pointer-events: none; }
#ed p { margin: 0 0 9px; }
#ed h1 { font-size: 24px; color: #0a8f56; margin: 18px 0 8px; line-height: 1.3; }
#ed h1.title { font-size: 34px; color: #0b0e22; margin-top: 2px; }
#ed h2 { font-size: 20px; color: #1f2a3a; margin: 16px 0 6px; line-height: 1.3; }
#ed h3, #ed h4, #ed h5, #ed h6 { font-size: 17.5px; color: #41597a; margin: 14px 0 4px; }
#ed blockquote { margin: 8px 0 10px; padding: 2px 0 2px 12px; border-left: 3px solid #0ba765; color: #41597a; font-style: italic; }
#ed ul, #ed ol { margin: 0 0 9px; padding-left: 26px; }
#ed li { margin: 0 0 4px; }
#ed table { border-collapse: collapse; width: 100%; table-layout: fixed; margin: 6px 0 10px; }
#ed td, #ed th { border: 1px solid #aab4c0; padding: 5px 6px; vertical-align: top; font-size: 14.5px; }
#ed th { background: #e3f6ec; font-weight: bold; text-align: left; }
#ed td p, #ed th p { margin: 0; }
#ed td.on, #ed th.on { box-shadow: inset 0 0 0 2px #34d399; }
#ed img { max-width: 100%; height: auto; border-radius: 3px; vertical-align: bottom; }
#ed img.sel { outline: 3px solid #0ba765; outline-offset: 2px; }
#ed hr { border: 0; border-top: 1.5px solid #bfc7d1; margin: 14px 0; }
#ed .pb { border-top: 2px dashed #9aa6b4; margin: 24px -26px; height: 0; position: relative; }
#ed .pb::after { content: 'নতুন পাতা'; position: absolute; top: -10px; left: 50%; transform: translateX(-50%);
  background: #fff; color: #8a97a8; font-size: 11px; padding: 0 8px; }
#ed a { color: #168ffb; }
</style></head>
<body><div id="page"><div id="ed" contenteditable="true" spellcheck="false"></div></div>
<script>
(function () {
  var ed = document.getElementById('ed');
  var lastRange = null, timer = null, selImg = null, onCell = null;
  var CONTENT_PT = 481.6;
  var BLOCK = tags('P DIV H1 H2 H3 H4 H5 H6 BLOCKQUOTE PRE UL OL LI TABLE HR SECTION ARTICLE HEADER FOOTER');
  var PARA = tags('P DIV H1 H2 H3 H4 H5 H6 LI BLOCKQUOTE TD TH PRE');
  function tags(s) { var o = {}; s.split(' ').forEach(function (t) { o[t] = true; }); return o; }
  function minorHeading(t) { return t === 'H3' || t === 'H4' || t === 'H5' || t === 'H6'; }

  function host() { return window.Android; }
  function inEd(n) { return !!n && (n === ed || ed.contains(n)); }
  function el(n) { return n && n.nodeType === 3 ? n.parentNode : n; }
  function saveSel() {
    var s = window.getSelection();
    if (s.rangeCount && inEd(s.anchorNode)) lastRange = s.getRangeAt(0).cloneRange();
  }
  function restore() {
    ed.focus({ preventScroll: true });
    if (lastRange) { var s = window.getSelection(); s.removeAllRanges(); s.addRange(lastRange); }
  }
  function blockOf(n) {
    n = el(n);
    while (n && n !== ed) { if (PARA[n.tagName]) return n; n = n.parentNode; }
    return null;
  }
  function cellOf(n) {
    n = el(n);
    while (n && n !== ed) { if (n.tagName === 'TD' || n.tagName === 'TH') return n; n = n.parentNode; }
    return null;
  }
  function q(c) { try { return document.queryCommandState(c); } catch (e) { return false; } }
  function words() { var t = ed.innerText.trim(); return t ? t.split(/\s+/).length : 0; }
  function hex(c) {
    var m = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/.exec(c || '');
    if (!m) return null;
    if (m[4] !== undefined && parseFloat(m[4]) < 0.05) return null;
    return '#' + [m[1], m[2], m[3]].map(function (v) { var h = parseInt(v, 10).toString(16); return h.length < 2 ? '0' + h : h; }).join('');
  }
  function blankCheck() {
    var blank = ed.textContent.trim() === '' && !ed.querySelector('img,table,hr,.pb,li');
    ed.classList.toggle('blank', blank);
  }

  function state() {
    saveSel();
    blankCheck();
    var s = window.getSelection();
    var node = s.rangeCount ? s.anchorNode : null;
    var b = blockOf(node);
    var kind = 'p';
    if (b) {
      var t = b.tagName;
      if (t === 'H1') kind = b.classList.contains('title') ? 'title' : 'h1';
      else if (t === 'H2') kind = 'h2';
      else if (minorHeading(t)) kind = 'h3';
      else if (b.closest('blockquote')) kind = 'quote';
    }
    var list = '';
    var e = el(node);
    if (e && e.closest) { var li = e.closest('li'); if (li && inEd(li)) list = li.parentNode.tagName === 'OL' ? 'ol' : 'ul'; }
    var align = 'left';
    if (b) { var a = getComputedStyle(b).textAlign; align = a === 'center' ? 'center' : (a === 'right' || a === 'end') ? 'right' : a === 'justify' ? 'justify' : 'left'; }
    var cell = cellOf(node);
    if (onCell && onCell !== cell) onCell.classList.remove('on');
    onCell = cell;
    if (cell) cell.classList.add('on');
    var st = { b: q('bold'), i: q('italic'), u: q('underline'), s: q('strikeThrough'), kind: kind, list: list,
      align: align, table: !!cell, img: !!selImg, words: words() };
    if (host()) host().state(JSON.stringify(st));
  }

  function cleanHtml() {
    if (onCell) onCell.classList.remove('on');
    if (selImg) selImg.classList.remove('sel');
    var h = ed.innerHTML;
    if (onCell) onCell.classList.add('on');
    if (selImg) selImg.classList.add('sel');
    return h;
  }
  function firstLine() { return (ed.innerText.trim().split('\n')[0] || '').slice(0, 60); }
  function changed() {
    clearTimeout(timer);
    timer = setTimeout(function () { if (host()) host().changed(cleanHtml(), firstLine()); }, 600);
  }
  function selectImage(img) {
    if (selImg) selImg.classList.remove('sel');
    selImg = img;
    if (img) img.classList.add('sel');
  }

  document.execCommand('defaultParagraphSeparator', false, 'p');
  document.addEventListener('selectionchange', function () { if (inEd(document.getSelection().anchorNode)) state(); });
  ed.addEventListener('input', function () { changed(); state(); });
  ed.addEventListener('keyup', state);
  ed.addEventListener('click', function (ev) { selectImage(ev.target.tagName === 'IMG' ? ev.target : null); state(); });
  ed.addEventListener('paste', function (ev) {
    var t = (ev.clipboardData || window.clipboardData).getData('text/plain');
    if (t) { ev.preventDefault(); document.execCommand('insertText', false, t); }
  });

  function cssOn(on) { document.execCommand('styleWithCSS', false, on); }

  // ------------------------------------------------------------------ export to the app's model
  function kindOf(b) {
    if (!b || b === ed) return 'normal';
    var t = b.tagName;
    if (t === 'H1') return b.classList.contains('title') ? 'title' : 'h1';
    if (t === 'H2') return 'h2';
    if (minorHeading(t)) return 'h3';
    if (b.closest && b.closest('blockquote')) return 'quote';
    return 'normal';
  }
  function alignOf(b) {
    var a = getComputedStyle(b).textAlign;
    return a === 'center' ? 'center' : (a === 'right' || a === 'end') ? 'right' : a === 'justify' ? 'justify' : 'left';
  }
  function isBlock(n) { return n.nodeType === 1 && (BLOCK[n.tagName] || n.classList.contains('pb')); }
  function hasBlockChild(e) { for (var c = e.firstChild; c; c = c.nextSibling) if (isBlock(c)) return true; return false; }
  function run(text, e, block, base) {
    var cs = getComputedStyle(e), r = { x: text };
    var w = cs.fontWeight;
    if (w === 'bold' || parseInt(w, 10) >= 600) r.b = true;
    if (cs.fontStyle === 'italic' || cs.fontStyle === 'oblique') r.i = true;
    var u = false, s = false, bg = null, a = null;
    for (var x = e; x && x.nodeType === 1; x = x.parentNode) {
      var c2 = getComputedStyle(x), d = c2.textDecorationLine || c2.textDecoration || '';
      if (d.indexOf('underline') >= 0) u = true;
      if (d.indexOf('line-through') >= 0) s = true;
      if (!bg && x !== block && x.tagName !== 'TD' && x.tagName !== 'TH') bg = hex(c2.backgroundColor);
      if (!a && x.tagName === 'A') a = x.getAttribute('href');
      if (x === block || x === ed) break;
    }
    if (u) r.u = true;
    if (s) r.s = true;
    if (bg) r.h = bg;
    if (a) r.a = a;
    if (cs.color !== base.color) r.c = hex(cs.color);
    var fz = parseFloat(cs.fontSize), bz = parseFloat(base.fontSize);
    if (Math.abs(fz - bz) > 0.5) r.z = Math.round(fz * 0.75 * 2) / 2;
    return r;
  }
  function img(e) {
    var cw = ed.clientWidth || 1, rc = e.getBoundingClientRect();
    var w = rc.width > 0 ? rc.width / cw * CONTENT_PT : (e.naturalWidth || 400) * 0.75;
    var h = rc.height > 0 ? rc.height / cw * CONTENT_PT : (e.naturalHeight || 300) * 0.75;
    return { t: 'img', src: e.src, w: Math.min(w, CONTENT_PT), h: h * Math.min(1, CONTENT_PT / w) };
  }
  function para(nodes, block, out, li) {
    var base = getComputedStyle(block);
    var info = { t: 'p', kind: kindOf(block), align: alignOf(block), list: li ? li.list : 'none', level: li ? li.level : 0 };
    var runs = [], emitted = false, sawImg = false;
    function emit() {
      var p = {}; for (var k in info) p[k] = info[k];
      p.runs = runs; out.push(p); runs = []; emitted = true;
    }
    function visit(n) {
      if (n.nodeType === 3) { if (n.nodeValue) runs.push(run(n.nodeValue, n.parentNode, block, base)); return; }
      if (n.nodeType !== 1) return;
      if (n.tagName === 'BR') { runs.push(run('\n', n.parentNode, block, base)); return; }
      if (n.tagName === 'IMG') { if (runs.length) emit(); out.push(img(n)); sawImg = true; return; }
      if (n.tagName === 'UL' || n.tagName === 'OL' || n.tagName === 'TABLE') return;
      for (var c = n.firstChild; c; c = c.nextSibling) visit(c);
    }
    nodes.forEach(visit);
    if (runs.length && runs[runs.length - 1].x === '\n') runs.pop();
    var onlySpace = runs.every(function (r) { return !r.x.trim(); });
    if (runs.length && !(sawImg && onlySpace)) emit();
    else if (!emitted && !sawImg) emit();
  }
  function kids(e) { var a = []; for (var c = e.firstChild; c; c = c.nextSibling) a.push(c); return a; }
  function list(e, out, level) {
    var tag = e.tagName === 'OL' ? 'number' : 'bullet';
    kids(e).forEach(function (n) {
      if (n.nodeType !== 1) return;
      if (n.tagName === 'LI') {
        var inl = [], nested = [];
        kids(n).forEach(function (c) { if (c.nodeType === 1 && (c.tagName === 'UL' || c.tagName === 'OL')) nested.push(c); else inl.push(c); });
        para(inl, n, out, { list: tag, level: level });
        nested.forEach(function (x) { list(x, out, level + 1); });
      } else if (n.tagName === 'UL' || n.tagName === 'OL') {
        list(n, out, level + 1);
      }
    });
  }
  function table(e) {
    var rows = [], header = false;
    for (var i = 0; i < e.rows.length; i++) {
      var tr = e.rows[i], row = [];
      for (var j = 0; j < tr.cells.length; j++) {
        var td = tr.cells[j];
        if (i === 0 && td.tagName === 'TH') header = true;
        var sub = []; walk(td, sub);
        var cell = { paras: sub.filter(function (b) { return b.t === 'p'; }) };
        if (td.style.backgroundColor) cell.bg = hex(getComputedStyle(td).backgroundColor);
        row.push(cell);
      }
      rows.push(row);
    }
    return { t: 'table', header: header, rows: rows };
  }
  function walk(container, out) {
    var inline = [];
    function flush() { if (inline.length) { para(inline, container, out, null); inline = []; } }
    kids(container).forEach(function (n) {
      if (n.nodeType === 3) { if (n.nodeValue.trim() || inline.length) inline.push(n); return; }
      if (n.nodeType !== 1) return;
      var t = n.tagName;
      if (n.classList.contains('pb')) { flush(); out.push({ t: 'pb' }); return; }
      if (t === 'HR') { flush(); out.push({ t: 'hr' }); return; }
      if (t === 'TABLE') { flush(); out.push(table(n)); return; }
      if (t === 'UL' || t === 'OL') { flush(); list(n, out, 0); return; }
      if (t === 'IMG') { flush(); out.push(img(n)); return; }
      if (isBlock(n)) { flush(); if (hasBlockChild(n)) walk(n, out); else para(kids(n), n, out, null); return; }
      inline.push(n);
    });
    flush();
  }

  function caretIn(n) {
    var r = document.createRange(); r.selectNodeContents(n); r.collapse(true);
    var s = window.getSelection(); s.removeAllRanges(); s.addRange(r); saveSel();
  }
  // Tables, lines and page breaks go between paragraphs (never inside a cell or a list).
  function insertBlock(node) {
    restore();
    var s = window.getSelection();
    var top = s.rangeCount ? el(s.anchorNode) : null;
    while (top && top !== ed && top.parentNode !== ed) top = top.parentNode;
    if (top === ed) top = null;
    var after = document.createElement('p'); after.innerHTML = '<br>';
    if (!top) { ed.appendChild(node); ed.appendChild(after); }
    else if (top.textContent.trim() === '' && !top.querySelector('img,table,hr,.pb')) { ed.replaceChild(node, top); ed.insertBefore(after, node.nextSibling); }
    else { ed.insertBefore(node, top.nextSibling); ed.insertBefore(after, node.nextSibling); }
    return after;
  }
  ed.addEventListener('keydown', function (ev) {
    if (ev.key !== 'Tab') return;
    var cell = cellOf(window.getSelection().anchorNode);
    if (!cell) return;
    ev.preventDefault();
    var cells = Array.prototype.slice.call(cell.closest('table').querySelectorAll('td,th'));
    var i = cells.indexOf(cell) + (ev.shiftKey ? -1 : 1);
    if (i >= cells.length) { N.tableOp('rowBelow'); cells = Array.prototype.slice.call(cell.closest('table').querySelectorAll('td,th')); }
    if (i >= 0 && i < cells.length) caretIn(cells[i].querySelector('p') || cells[i]);
    state();
  });

  // ------------------------------------------------------------------ commands from the app
  window.N = {
    load: function (html) { ed.innerHTML = html || '<p><br></p>'; lastRange = null; selImg = null; onCell = null; blankCheck(); window.scrollTo(0, 0); },
    html: cleanHtml,
    first: firstLine,
    model: function () { var out = []; walk(ed, out); return JSON.stringify({ blocks: out }); },
    text: function () { return ed.innerText; },
    focusEnd: function () {
      ed.focus();
      var r = document.createRange(); r.selectNodeContents(ed); r.collapse(false);
      var s = window.getSelection(); s.removeAllRanges(); s.addRange(r); state();
    },
    cmd: function (c, v) {
      restore(); cssOn(c === 'foreColor' || c === 'hiliteColor');
      document.execCommand(c, false, v === undefined ? null : v);
      cssOn(false); saveSel(); changed(); state();
    },
    block: function (k) {
      restore();
      var tag = (k === 'title' || k === 'h1') ? 'H1' : k === 'h2' ? 'H2' : k === 'h3' ? 'H3' : k === 'quote' ? 'BLOCKQUOTE' : 'P';
      var cur = blockOf(window.getSelection().anchorNode);
      if (cur && cur.tagName === 'LI' && tag !== 'P') {
        document.execCommand(cur.parentNode.tagName === 'OL' ? 'insertOrderedList' : 'insertUnorderedList');
      }
      if (cur && cur.closest('blockquote') && tag !== 'BLOCKQUOTE') document.execCommand('outdent');
      document.execCommand('formatBlock', false, tag);
      var b = blockOf(window.getSelection().anchorNode);
      if (b && b.tagName === 'H1') { if (k === 'title') b.classList.add('title'); else b.classList.remove('title'); }
      saveSel(); changed(); state();
    },
    size: function (px) {
      restore(); cssOn(false);
      document.execCommand('fontSize', false, '7');
      var made = [];
      ed.querySelectorAll('font[size="7"]').forEach(function (f) {
        var sp = document.createElement('span'); sp.style.fontSize = px + 'px';
        while (f.firstChild) sp.appendChild(f.firstChild);
        f.parentNode.replaceChild(sp, f); made.push(sp);
      });
      ed.querySelectorAll('span').forEach(function (sp) { if (/xxx-large/.test(sp.style.fontSize)) { sp.style.fontSize = px + 'px'; made.push(sp); } });
      if (made.length) {
        var r = document.createRange(); r.setStartBefore(made[0]); r.setEndAfter(made[made.length - 1]);
        var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
      }
      saveSel(); changed(); state();
    },
    link: function (url) {
      restore();
      var s = window.getSelection();
      if (s.isCollapsed) {
        var a = document.createElement('a'); a.href = url; a.textContent = url;
        document.execCommand('insertHTML', false, a.outerHTML + '&nbsp;');
      } else {
        document.execCommand('createLink', false, url);
      }
      saveSel(); changed(); state();
    },
    insertHtml: function (h) { restore(); document.execCommand('insertHTML', false, h); saveSel(); changed(); state(); },
    table: function (r, c) {
      var t = document.createElement('table'), body = document.createElement('tbody');
      for (var i = 0; i < r; i++) {
        var tr = document.createElement('tr');
        for (var j = 0; j < c; j++) { var td = document.createElement(i === 0 ? 'th' : 'td'); td.innerHTML = '<p><br></p>'; tr.appendChild(td); }
        body.appendChild(tr);
      }
      t.appendChild(body);
      insertBlock(t);
      caretIn(t.rows[0].cells[0].firstChild);
      changed(); state();
    },
    tableOp: function (op) {
      restore();
      var cell = cellOf(window.getSelection().anchorNode);
      if (!cell) return;
      var row = cell.parentNode, tbl = row.closest('table'), idx = Array.prototype.indexOf.call(row.children, cell);
      if (op === 'rowAbove' || op === 'rowBelow') {
        var nr = document.createElement('tr');
        for (var i = 0; i < row.children.length; i++) { var td = document.createElement('td'); td.innerHTML = '<p><br></p>'; nr.appendChild(td); }
        row.parentNode.insertBefore(nr, op === 'rowAbove' ? row : row.nextSibling);
        if (op === 'rowBelow') caretIn(nr.cells[Math.min(idx, nr.cells.length - 1)].firstChild);
      } else if (op === 'colLeft' || op === 'colRight') {
        for (var k = 0; k < tbl.rows.length; k++) {
          var ref = tbl.rows[k].children[idx];
          var nc = document.createElement(ref && ref.tagName === 'TH' ? 'th' : 'td'); nc.innerHTML = '<p><br></p>';
          if (!ref) tbl.rows[k].appendChild(nc); else tbl.rows[k].insertBefore(nc, op === 'colLeft' ? ref : ref.nextSibling);
        }
      } else if (op === 'delRow') {
        if (tbl.rows.length <= 1) tbl.remove(); else row.remove();
      } else if (op === 'delCol') {
        if (row.children.length <= 1) tbl.remove();
        else for (var m = 0; m < tbl.rows.length; m++) { var x = tbl.rows[m].children[idx]; if (x) x.remove(); }
      } else if (op === 'header') {
        var first = tbl.rows[0], toTh = first.cells[0] && first.cells[0].tagName !== 'TH';
        kids(first).forEach(function (c0) {
          if (c0.nodeType !== 1) return;
          var n = document.createElement(toTh ? 'th' : 'td'); while (c0.firstChild) n.appendChild(c0.firstChild);
          first.replaceChild(n, c0);
        });
      } else if (op === 'delTable') {
        tbl.remove();
      }
      onCell = null; changed(); state();
    },
    image: function (src, pct) { N.insertHtml('<img src="' + src + '" style="width:' + pct + '%">'); },
    imgSize: function (pct) { if (selImg) { selImg.style.width = pct + '%'; changed(); } },
    imgDelete: function () { if (selImg) { selImg.remove(); selImg = null; changed(); state(); } },
    hr: function () { caretIn(insertBlock(document.createElement('hr'))); changed(); state(); },
    pageBreak: function () {
      var d = document.createElement('div'); d.className = 'pb'; d.contentEditable = 'false';
      caretIn(insertBlock(d)); changed(); state();
    }
  };
})();
</script></body></html>"""
}
