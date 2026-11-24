// Download a project's requirements as Word or PDF. Uses markdown.js

function documentParts(detail) {
  const p = detail.project;
  const details = [
    p.clientName && `Client: ${p.clientName}`,
    `Type: ${p.industry || 'Not specified'}`,
    `Date: ${new Date(p.submittedAt || Date.now()).toLocaleDateString()}`
  ].filter(Boolean);
  return { title: p.name, details, sections: [['Product Requirements', detail.prd || ''], ['System Requirements', detail.srs || '']] };
}

function documentFileName(title) {
  return `${title.replace(/[^\p{L}\p{M}\p{N} _-]/gu, '').trim().replace(/\s+/g, '-').slice(0, 80) || 'project'}-requirements`;
}

let docxLoading = null;
function loadDocx() {
  docxLoading = docxLoading || new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = '/vendor/docx-9.5.1.js';
    script.onload = () => resolve(window.docx);
    script.onerror = () => {
      docxLoading = null;
      script.remove();
      reject(new Error('Could not load the Word export. Please try again.'));
    };
    document.head.appendChild(script);
  });
  return docxLoading;
}

async function downloadWord(detail) {
  const d = await loadDocx();
  const { title, details, sections } = documentParts(detail);
  const headings = [d.HeadingLevel.HEADING_2, d.HeadingLevel.HEADING_3, d.HeadingLevel.HEADING_4];
  const runs = (text) => text.split(/(\*\*.+?\*\*|`[^`]+`)/).filter(Boolean).map(part => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return new d.TextRun({ text: part.slice(2, -2), bold: true });
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) return new d.TextRun({ text: part.slice(1, -1), font: 'Courier New' });
    return new d.TextRun(part);
  });

  const children = [new d.Paragraph({ text: title, heading: d.HeadingLevel.TITLE })];
  details.forEach(text => children.push(new d.Paragraph({ text })));
  sections.forEach(([name, markdown]) => {
    children.push(new d.Paragraph({ text: name, heading: d.HeadingLevel.HEADING_1 }));
    markdown.split('\n').forEach(line => {
      const heading = line.match(/^\s*(#{1,6})\s+(.*)$/);
      const bullet = line.match(/^(\s*)[-*•]\s+(.*)$/);
      if (!line.trim() || /^\s*(-{3,}|\*{3,})\s*$/.test(line)) return;
      if (heading) {
        children.push(new d.Paragraph({ children: runs(heading[2].replace(/\*\*/g, '')), heading: headings[Math.min(heading[1].length, 3) - 1] }));
      } else if (bullet) {
        children.push(new d.Paragraph({ children: runs(bullet[2]), bullet: { level: Math.min(Math.floor(bullet[1].length / 2), 3) } }));
      } else {
        children.push(new d.Paragraph({ children: runs(line.trim()), spacing: { after: 120 } }));
      }
    });
  });

  const blob = await d.Packer.toBlob(new d.Document({ creator: 'Thinkvalley Softwares', title, sections: [{ children }] }));
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${documentFileName(title)}.docx`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 60000);
}

// Uses the browser's print dialog ("Save as PDF"), which supports every language
function printPdf(detail) {
  const { title, details, sections } = documentParts(detail);
  const area = document.createElement('div');
  area.id = 'printArea';
  area.innerHTML = `<h1>${escapeHtml(title)}</h1><p>${details.map(escapeHtml).join('<br>')}</p>`
    + sections.map(([name, markdown]) => `<h2>${name}</h2>${renderMarkdown(markdown)}`).join('');

  const style = document.createElement('style');
  style.textContent = `
    @media screen { #printArea { display: none; } }
    @media print {
      @page { margin: 18mm; }
      body > *:not(#printArea) { display: none !important; }
      #printArea { color: #000; font: 11pt/1.5 sans-serif; }
      #printArea h1, #printArea h2, #printArea h3, #printArea h4, #printArea h5 { margin: 14pt 0 6pt; break-after: avoid; }
      #printArea p { margin: 0 0 6pt; }
      #printArea ul, #printArea ol { padding-left: 20pt; margin: 0 0 6pt; }
    }`;

  const oldTitle = document.title;
  document.getElementById('printArea')?.remove();
  document.body.append(style, area);
  document.title = documentFileName(title); // suggested file name
  window.addEventListener('afterprint', () => {
    document.title = oldTitle;
    area.remove();
    style.remove();
  }, { once: true });
  window.print();
}
