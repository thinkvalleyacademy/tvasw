function escapeHtml(text) {
  const el = document.createElement('div');
  el.textContent = text;
  return el.innerHTML;
}

// Small Markdown subset. Input is escaped first, so only the tags added here reach the page
function renderMarkdown(text) {
  const inline = (s) => s
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');

  const out = [];
  let list = null;
  let para = [];
  const flush = () => {
    if (para.length) out.push(`<p>${para.join('<br>')}</p>`);
    para = [];
  };
  const closeList = () => {
    if (list) out.push(`</${list}>`);
    list = null;
  };

  escapeHtml(text).split('\n').forEach(line => {
    const heading = line.match(/^\s*(#{1,6})\s+(.*)$/);
    const item = line.match(/^\s*(?:[-*•]|(\d+)[.)])\s+(.*)$/);
    if (heading) {
      flush(); closeList();
      const level = Math.min(heading[1].length + 2, 6);
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`);
    } else if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) {
      flush(); closeList();
      out.push('<hr>');
    } else if (item) {
      flush();
      const type = item[1] ? 'ol' : 'ul';
      if (list !== type) {
        closeList();
        out.push(type === 'ol' ? `<ol start="${item[1]}">` : '<ul>');
        list = type;
      }
      out.push(`<li>${inline(item[2])}</li>`);
    } else if (!line.trim()) {
      flush(); closeList();
    } else {
      closeList();
      para.push(inline(line.trim()));
    }
  });
  flush(); closeList();
  return out.join('');
}
