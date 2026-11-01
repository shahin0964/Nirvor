package com.nirvor.app.browser

/** Small scripts run inside web pages (reader mode, grabber, page source, display fixes). */
object PageScripts {

    /** The page's HTML. */
    const val SOURCE = "(function(){return document.documentElement.outerHTML;})()"

    /** Every image, video, audio and link on the page, as JSON: [{t:'img|video|audio|link', u:url, n:text}]. */
    const val GRAB = """
(function(){
  var out=[]; var seen={};
  function add(t,u,n){ if(!u||u.indexOf('data:')==0||u.indexOf('blob:')==0||u.indexOf('javascript:')==0) return;
    try{u=new URL(u,location.href).href;}catch(e){return;}
    var k=t+'|'+u; if(seen[k]) return; seen[k]=1; out.push({t:t,u:u,n:(n||'').trim().substring(0,120)}); }
  document.querySelectorAll('img').forEach(function(e){ add('img', e.currentSrc||e.src, e.alt);
    if(e.srcset){ var best=e.srcset.split(',').pop().trim().split(' ')[0]; add('img',best,e.alt);} });
  document.querySelectorAll('video,video source').forEach(function(e){ add('video', e.currentSrc||e.src, document.title); });
  document.querySelectorAll('audio,audio source').forEach(function(e){ add('audio', e.currentSrc||e.src, document.title); });
  document.querySelectorAll('a[href]').forEach(function(e){ add('link', e.href, e.innerText||e.title); });
  document.querySelectorAll('meta[property="og:image"],meta[property="og:video"]').forEach(function(e){
    add(e.getAttribute('property')=='og:video'?'video':'img', e.content, document.title); });
  return JSON.stringify(out.slice(0,1500));
})()
"""

    /** Reader mode: the main text of the page as JSON {title, byline, paragraphs:[...], image}. */
    const val READER = """
(function(){
  function score(el){ var ps=el.querySelectorAll('p'); var n=0; ps.forEach(function(p){ n+=p.innerText.length; }); return n; }
  var cand=document.querySelector('article')||document.querySelector('main')||document.querySelector('[role=main]');
  if(!cand||score(cand)<400){ var best=document.body, bs=0;
    document.querySelectorAll('div,section,td').forEach(function(d){ var s=score(d)-d.querySelectorAll('a').length*20; if(s>bs){bs=s;best=d;} });
    cand=best; }
  var parts=[];
  cand.querySelectorAll('h1,h2,h3,p,li,blockquote,pre').forEach(function(e){
    var t=(e.innerText||'').trim(); if(t.length<2) return;
    var tag=e.tagName.toLowerCase(); if(tag=='li') t='• '+t; if(tag[0]=='h') t='## '+t;
    parts.push(t); });
  var img=document.querySelector('meta[property="og:image"]');
  var by=document.querySelector('meta[name="author"]');
  return JSON.stringify({title:document.title, byline: by?by.content:'', image: img?img.content:'', paragraphs: parts.slice(0,800)});
})()
"""

    /** Lets the user zoom pages that block zooming. */
    const val FORCE_ZOOM = """
(function(){ var m=document.querySelector('meta[name=viewport]');
  if(!m){ m=document.createElement('meta'); m.name='viewport'; document.head.appendChild(m); }
  m.content='width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes'; })()
"""

    /** Lets the user select and copy text on pages that block it. */
    const val FORCE_SELECT = """
(function(){ var s=document.getElementById('nirvor-select'); if(s) return;
  s=document.createElement('style'); s.id='nirvor-select';
  s.innerHTML='*{-webkit-user-select:text!important;user-select:text!important;-webkit-touch-callout:default!important}';
  (document.head||document.documentElement).appendChild(s);
  ['copy','cut','paste','selectstart','contextmenu'].forEach(function(e){ document.addEventListener(e,function(ev){ev.stopPropagation();},true); }); })()
"""

    /** Dark pages: inverts the page colours but keeps photos and videos natural. */
    const val DARK_ON = """
(function(){ if(document.getElementById('nirvor-dark')) return;
  var s=document.createElement('style'); s.id='nirvor-dark';
  s.innerHTML='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#fff!important}'+
   'img,video,picture,canvas,iframe,svg image,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)!important}';
  (document.head||document.documentElement).appendChild(s); })()
"""

    const val DARK_OFF = "(function(){ var s=document.getElementById('nirvor-dark'); if(s) s.remove(); })()"

    /** Rendering modes (applied as a page filter). */
    fun rendering(mode: String): String {
        val css = when (mode) {
            "grayscale" -> "html{filter:grayscale(1)!important}"
            "invert" -> "html{filter:invert(1)!important}"
            "invert_grayscale" -> "html{filter:invert(1) grayscale(1)!important}"
            "high_contrast" -> "html{filter:contrast(1.6)!important}"
            else -> ""
        }
        return """
(function(){ var s=document.getElementById('nirvor-render'); if(!s){ s=document.createElement('style'); s.id='nirvor-render';
  (document.head||document.documentElement).appendChild(s);} s.innerHTML='$css'; })()
"""
    }
}
