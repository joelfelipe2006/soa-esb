package br.senai.fatesg.soa.esb;

/**
 * Painel web de monitoramento do barramento (http://localhost:8080).
 * Mostra os serviços registrados, a fila de entrega garantida e cada passo de cada
 * mensagem em tempo real. Também tem um formulário para fazer pedidos na demo.
 */
final class Dashboard {

    private Dashboard() {
    }

    static final String HTML = """
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ESB · Painel do Barramento</title>
<style>
  :root {
    --bg: #0E1726; --panel: #15213A; --panel2: #1B2A47; --line: #2A3A55;
    --text: #F5F2EB; --muted: #AEB9CB; --orange: #E8772E; --blue: #6AA6E0;
    --green: #5CC48A; --red: #E5616B; --purple: #B79CF2; --yellow: #E8C547;
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text);
         font: 15px/1.45 "Segoe UI", system-ui, -apple-system, Roboto, Arial, sans-serif; }
  header { display: flex; align-items: baseline; gap: 16px; padding: 18px 28px 6px; flex-wrap: wrap; }
  h1 { margin: 0; font-size: 26px; letter-spacing: -0.5px; }
  h1 span { color: var(--orange); }
  .sub { color: var(--muted); }
  .live { margin-left: auto; color: var(--muted); font-size: 13px; }
  .live b { display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: var(--green);
            margin-right: 6px; animation: pulse 1.6s infinite; }
  .live.off b { background: var(--red); animation: none; }
  @keyframes pulse { 50% { opacity: .35; } }

  .topo { padding: 10px 28px 18px; }
  .consumer { display: flex; justify-content: center; }
  .box { background: var(--panel); border: 2px solid var(--line); border-radius: 12px; padding: 10px 16px;
         text-align: center; min-width: 150px; transition: border-color .2s, box-shadow .2s; }
  .box .n { font-weight: 700; }
  .box .f { font-size: 12px; color: var(--muted); }
  .wire { width: 2px; height: 18px; background: var(--line); margin: 0 auto; }
  .bus { background: linear-gradient(90deg, #E8772E, #F09A55); color: #0E1726; border-radius: 14px;
         padding: 10px 18px; display: flex; align-items: center; gap: 14px; flex-wrap: wrap; justify-content: center; }
  .bus .t { font-weight: 800; font-size: 18px; margin-right: 6px; }
  .bus .cap { background: rgba(14,23,38,.12); border-radius: 999px; padding: 3px 12px; font-size: 13px; font-weight: 600; }
  .services { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; }
  .svc .wire { margin-bottom: 0; }
  .box.on { border-color: var(--green); }
  .box.off { border-color: var(--red); opacity: .55; }
  .box.none { border-style: dashed; opacity: .45; }
  .box.hit { box-shadow: 0 0 0 4px rgba(232,119,46,.55); border-color: var(--orange); }
  .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 5px; }
  .on .dot { background: var(--green); } .off .dot { background: var(--red); } .none .dot { background: var(--muted); }
  .tag { display: inline-block; font-size: 11px; font-weight: 700; border-radius: 4px; padding: 0 5px; margin-left: 4px; }
  .tag.JSON { background: #23406B; color: #CFE2F8; } .tag.XML { background: #4B2E6B; color: #E4D6FB; }

  main { display: grid; grid-template-columns: 360px 1fr; gap: 20px; padding: 0 28px 28px; }
  section { background: var(--panel); border: 1px solid var(--line); border-radius: 14px; padding: 16px 18px; margin-bottom: 18px; }
  h2 { margin: 0 0 12px; font-size: 15px; text-transform: uppercase; letter-spacing: 1.5px; color: var(--muted); }
  label { display: block; font-size: 13px; color: var(--muted); margin: 8px 0 4px; }
  input, select { width: 100%; background: var(--panel2); color: var(--text); border: 1px solid var(--line);
                  border-radius: 8px; padding: 9px 10px; font: inherit; }
  .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  button { margin-top: 14px; width: 100%; background: var(--orange); color: #0E1726; font-weight: 700; border: 0;
           border-radius: 8px; padding: 11px; font: inherit; font-weight: 700; cursor: pointer; }
  button.ghost { background: transparent; color: var(--blue); border: 1px solid var(--line); margin-top: 8px; }
  .result { margin-top: 12px; border-radius: 10px; padding: 10px 12px; font-size: 14px; display: none; }
  .result.ok { display: block; background: rgba(92,196,138,.12); border: 1px solid var(--green); }
  .result.bad { display: block; background: rgba(229,97,107,.12); border: 1px solid var(--red); }
  .result.info { display: block; background: rgba(106,166,224,.12); border: 1px solid var(--blue); }
  .result b { display: block; margin-bottom: 4px; }
  .q { font-size: 13px; padding: 8px 10px; background: var(--panel2); border-radius: 8px; margin-bottom: 8px; }
  .empty { color: var(--muted); font-size: 13px; }

  .flow { max-height: calc(100vh - 330px); min-height: 360px; overflow: auto; }
  .msg { display: grid; grid-template-columns: 70px 128px 200px 1fr; gap: 10px; align-items: start;
         padding: 7px 4px; border-bottom: 1px solid var(--line); font-size: 13.5px; animation: in .4s; }
  @keyframes in { from { background: rgba(232,119,46,.25); } to { background: transparent; } }
  .msg .h { color: var(--muted); font-variant-numeric: tabular-nums; }
  .msg .k { font-weight: 700; font-size: 12px; border-radius: 5px; padding: 2px 7px; text-align: center; }
  .msg .p { color: var(--muted); }
  .msg .d { font-family: Consolas, "JetBrains Mono", monospace; font-size: 12.5px; word-break: break-word; }
  .k.REGISTRO { background: #1F3B2E; color: #9BE3BB; } .k.RECEBIDO, .k.RESPOSTA { background: #1D3150; color: #A9CDF2; }
  .k.CHAMADA, .k.ENTREGUE { background: #1F3B2E; color: #9BE3BB; } .k.ROTEADO { background: #4A3D10; color: #F2DD86; }
  .k.TRANSFORMADO { background: #3A2858; color: #D9C8FA; } .k.FILA { background: #2B3446; color: #C3CCDA; }
  .k.ERRO, .k.COMPENSACAO { background: #4A1F25; color: #F5B2B8; } .k.CONCLUIDO { background: #E8772E; color: #0E1726; }
  @media (max-width: 1000px) { main { grid-template-columns: 1fr; } .services { grid-template-columns: repeat(2, 1fr); }
    .msg { grid-template-columns: 60px 110px 1fr; } .msg .p { display: none; } }
</style>
</head>
<body>
<header>
  <h1>ESB <span>·</span> Barramento de Serviços</h1>
  <div class="sub">SOA com Enterprise Service Bus · Java puro</div>
  <div class="live" id="live"><b></b>ao vivo</div>
</header>

<div class="topo">
  <div class="consumer"><div class="box" id="box-loja"><div class="n">Loja (consumidor)</div><div class="f">só conhece o ESB</div></div></div>
  <div class="wire"></div>
  <div class="bus"><span class="t">ESB</span>
    <span class="cap">Registro</span><span class="cap">Roteamento</span><span class="cap">Transformação</span>
    <span class="cap">Orquestração</span><span class="cap">Entrega garantida</span><span class="cap">Monitoramento</span>
  </div>
  <div class="services" id="services"></div>
</div>

<main>
  <div>
    <section>
      <h2>Novo pedido</h2>
      <form id="form">
        <label>Cliente</label><input name="cliente" value="Maria">
        <div class="row2">
          <div><label>Produto</label>
            <select name="produto"><option>teclado</option><option>mouse</option><option>monitor</option><option>notebook</option></select></div>
          <div><label>Quantidade</label><input name="quantidade" type="number" min="1" value="1"></div>
        </div>
        <label>Forma de pagamento</label>
        <select name="formaPagamento"><option>PIX</option><option>CARTAO</option></select>
        <button type="submit">Enviar pedido ao ESB</button>
        <button type="button" class="ghost" id="btnEstoque">Consultar estoque pelo ESB</button>
      </form>
      <div class="result" id="result"></div>
    </section>
    <section>
      <h2>Fila de entrega garantida</h2>
      <div id="queue"><div class="empty">vazia</div></div>
    </section>
  </div>
  <section>
    <h2>Fluxo de mensagens (mais recente no topo)</h2>
    <div class="flow" id="flow"><div class="empty" id="flowEmpty">Nenhuma mensagem ainda.</div></div>
  </section>
</main>

<script>
const EXPECTED = [
  ['estoque', 'Estoque'], ['pagamento-pix', 'Pagamento PIX'], ['pagamento-cartao', 'Pagamento Cartão'],
  ['faturamento', 'Faturamento (legado)'], ['notificacao', 'Notificação']
];
let since = 0;
const LABEL = { COMPENSACAO: 'COMPENSAÇÃO', CONCLUIDO: 'CONCLUÍDO' };
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

function renderServices(list) {
  const byName = {};
  list.forEach(s => byName[s.nome] = s);
  const names = EXPECTED.map(e => e[0]).concat(list.map(s => s.nome).filter(n => !EXPECTED.some(e => e[0] === n)));
  const el = document.getElementById('services');
  el.innerHTML = names.map(n => {
    const s = byName[n];
    const label = (EXPECTED.find(e => e[0] === n) || [n, n])[1];
    const cls = !s ? 'none' : (s.online ? 'on' : 'off');
    const fmt = s ? '<span class="tag ' + esc(s.formato) + '">' + esc(s.formato) + '</span>' : '';
    const sub = !s ? 'não registrado' : (s.online ? esc(s.url.replace('http://', '')) : 'FORA DO AR');
    return '<div class="svc"><div class="wire"></div><div class="box ' + cls + '" id="box-' + esc(n) + '">' +
      '<div class="n"><span class="dot"></span>' + esc(label) + fmt + '</div><div class="f">' + sub + '</div></div></div>';
  }).join('');
}

function renderQueue(list) {
  const el = document.getElementById('queue');
  if (!list.length) { el.innerHTML = '<div class="empty">vazia: tudo entregue</div>'; return; }
  el.innerHTML = list.map(p => '<div class="q"><b>' + esc(p.id) + '</b> → ' + esc(p.servico) + '.' + esc(p.operacao) +
    '<br><span class="empty">' + esc(p.tentativas) + ' tentativa(s), aguardando o serviço voltar</span></div>').join('');
}

function flash(name) {
  const b = document.getElementById('box-' + name);
  if (!b) return;
  b.classList.add('hit');
  setTimeout(() => b.classList.remove('hit'), 900);
}

function addMsg(m) {
  const flow = document.getElementById('flow');
  const empty = document.getElementById('flowEmpty');
  if (empty) empty.remove();
  const div = document.createElement('div');
  div.className = 'msg';
  div.innerHTML = '<div class="h">' + esc(m.hora) + '</div><div class="k ' + esc(m.tipo) + '">' + esc(LABEL[m.tipo] || m.tipo) + '</div>' +
    '<div class="p">' + esc(m.de) + ' → ' + esc(m.para) + (m.formato && m.formato !== '-' ? ' · ' + esc(m.formato) : '') + '</div>' +
    '<div class="d">' + esc(m.detalhe) + '</div>';
  flow.prepend(div);
  while (flow.children.length > 250) flow.lastChild.remove();
  if (m.tipo === 'CHAMADA' || m.tipo === 'ENTREGUE') flash(m.para);
  if (m.tipo === 'RECEBIDO' || m.tipo === 'CONCLUIDO') flash('loja');
}

async function tick() {
  try {
    const r = await fetch('/api/estado?since=' + since);
    const d = await r.json();
    renderServices(d.servicos);
    renderQueue(d.fila);
    d.mensagens.forEach(m => { addMsg(m); since = m.seq; });
    document.getElementById('live').className = 'live';
  } catch (e) {
    document.getElementById('live').className = 'live off';
  }
}
setInterval(tick, 800);
tick();

function showResult(cls, html) {
  const el = document.getElementById('result');
  el.className = 'result ' + cls;
  el.innerHTML = html;
}

document.getElementById('form').addEventListener('submit', async ev => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  const body = { cliente: f.get('cliente'), produto: f.get('produto'),
                 quantidade: Number(f.get('quantidade')), formaPagamento: f.get('formaPagamento') };
  try {
    const r = await fetch('/esb/pedidos', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    const d = await r.json();
    if (d.status === 'APROVADO') {
      showResult('ok', '<b>' + esc(d.pedidoId) + ' APROVADO</b>R$ ' + Number(d.valor).toFixed(2) + ' · ' + esc(d.pagamento) +
        '<br>Nota fiscal: ' + esc(d.notaFiscal));
    } else if (d.status === 'REJEITADO') {
      showResult('bad', '<b>' + esc(d.pedidoId) + ' REJEITADO</b>' + esc(d.motivo));
    } else {
      showResult('bad', '<b>Erro</b>' + esc(d.erro || JSON.stringify(d)));
    }
  } catch (e) {
    showResult('bad', '<b>ESB fora do ar</b>' + esc(e.message));
  }
});

document.getElementById('btnEstoque').addEventListener('click', async () => {
  try {
    const d = await (await fetch('/esb/estoque')).json();
    if (d.erro) { showResult('bad', '<b>Estoque</b>' + esc(d.erro)); return; }
    showResult('info', '<b>Estoque (via ESB)</b>' + (d.produtos || []).map(p =>
      esc(p.produto) + ': ' + esc(p.disponivel) + ' un · R$ ' + Number(p.preco).toFixed(2)).join('<br>'));
  } catch (e) { showResult('bad', '<b>ESB fora do ar</b>' + esc(e.message)); }
});
</script>
</body>
</html>
""";
}
