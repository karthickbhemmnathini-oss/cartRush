/* ===== CONFIG: change if your backend runs elsewhere ===== */
/* ===== CONFIG: change if your backend runs elsewhere ===== */
const API_BASE = "/api";

/* ===== helpers ===== */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const money = n => "₹" + Number(n || 0).toLocaleString("en-IN");
let user = JSON.parse(localStorage.getItem("cr_user") || "null");
let cartObj = null, cartCount = 0;

async function api(path, opts = {}) {
    const o = { ...opts };
    if (o.json) { o.method = o.method || "POST"; o.headers = { "Content-Type": "application/json" }; o.body = JSON.stringify(o.json); }
    const r = await fetch(API_BASE + path, o);
    const t = await r.text();
    if (!r.ok) {
        let m = ""; try { const j = JSON.parse(t); m = j.message || j.error; } catch {}
        throw new Error(m && m !== "Internal Server Error" ? m : `Request failed (${r.status})`);
    }
    try { return JSON.parse(t); } catch { return t; }
}
function toast(msg, err) {
    const t = $("#toast"); t.textContent = msg; t.className = "show" + (err ? " err" : "");
    clearTimeout(toast.t); toast.t = setTimeout(() => t.className = "", 2800);
}
const guard = fn => async (...a) => { try { await fn(...a); } catch (e) { toast(e.message, true); } };
const isManager = () => user && (user.role === "ADMIN" || user.role === "SUPPLIER");

const img = (p, cls = "") => p.imageUrl
    ? `<img class="pimg ${cls}" src="${esc(p.imageUrl)}" alt="${esc(p.name)}" loading="lazy" onerror="this.outerHTML='<div class=&quot;pimg ph ${cls}&quot;>${esc((p.name || "?")[0].toUpperCase())}</div>'">`
    : `<div class="pimg ph ${cls}">${esc((p.name || "?")[0].toUpperCase())}</div>`;
let ratings = {};
const stars = r => r ? `<span class="stars">★ ${r.avg}</span> <span class="mu">(${r.n})</span>` : `<span class="mu">No reviews</span>`;

/* ===== cart ===== */
async function getCart() {
    if (!user) return null;
    if (cartObj) return cartObj;
    try { cartObj = await api(`/carts/user/${user.id}`); }
    catch { cartObj = await api(`/carts/user/${user.id}`, { method: "POST" }); }
    return cartObj;
}
async function cartItems() {
    const c = await getCart(); if (!c) return [];
    const items = await api(`/cart-items/cart/${c.id}`);
    cartCount = items.reduce((s, i) => s + i.quantity, 0); renderNav();
    return items;
}
const addToCart = guard(async (pid, qty = 1) => {
    if (user && user.role === "SUPPLIER") { location.hash = "#/manage"; return toast("Suppliers cannot add products to a customer cart.", true); }
    if (!user) { location.hash = "#/login"; return toast("Please log in first", true); }
    const c = await getCart(), items = await cartItems();
    const ex = items.find(i => i.product.id === pid);
    if (ex) await api(`/cart-items/${ex.id}?quantity=${ex.quantity + qty}`, { method: "PUT" });
    else await api(`/cart-items/cart/${c.id}/product/${pid}?quantity=${qty}`, { method: "POST" });
    await cartItems(); toast("Added to cart ✓");
});

/* ===== nav ===== */
function renderNav() {
    if (user && user.role === "SUPPLIER") {
        $("#nav").innerHTML = `<a href="#/supplier-dashboard">Dashboard</a><a href="#/supplier-products">My Products</a><a href="#/supplier-orders">Supplier Orders</a><button onclick="logout()">Logout (${esc(user.name)})</button>`;
        return;
    }
    $("#nav").innerHTML = `<a href="#/">Shop</a>` + (user
        ? `<a href="#/cart">Cart<span class="badge">${cartCount}</span></a><a href="#/orders">Orders</a>` +
        (user.role === "ADMIN" ? `<a href="#/manage">Admin</a>` : "") +
        `<button onclick="logout()">Logout (${esc(user.name)})</button>`
        : `<a href="#/login">Login</a><a class="btn sm" href="#/register">Sign up</a>`);
}
function logout() { localStorage.removeItem("cr_user"); user = null; cartObj = null; cartCount = 0; renderNav(); location.hash = "#/"; }

/* ===== router ===== */
const view = h => $("#app").innerHTML = h;
const routes = [
    [/^#\/product\/(\d+)$/, productPage], [/^#\/cart$/, cartPage], [/^#\/orders$/, ordersPage],
    [/^#\/login$/, loginPage], [/^#\/register$/, registerPage],
    [/^#\/manage$/, managePage], [/^#\/supplier-dashboard$/, managePage], [/^#\/supplier-products$/, managePage], [/^#\/supplier-orders$/, supplierOrdersPage]
];
async function route() {
    const h = location.hash || "#/";
    window.scrollTo(0, 0);
    try {
        for (const [re, fn] of routes) { const m = h.match(re); if (m) return await fn(m[1]); }
        await homePage();
    } catch (e) { view(`<div class="empty">⚠️ ${esc(e.message)}<br><small>Is the backend running at ${API_BASE}?</small></div>`); }
}

/* ===== pages ===== */
const card = p => `<div class="card"><a href="#/product/${p.id}" style="text-decoration:none;color:inherit">
  ${img(p)}</a><div class="body">
  <h3>${esc(p.name)}</h3><div class="mu">${esc(p.brand || "")}</div><div>${stars(ratings[p.id])}</div>
  <div>${p.category ? `<span class="tag">${esc(p.category.name)}</span>` : ""}${p.waterproof ? `<span class="tag g">Waterproof</span>` : ""}</div>
  <div class="price">${money(p.price)}</div>
  ${p.stock > 0 ? `<button class="btn sm" onclick="addToCart(${p.id})">Add to cart</button>` : `<span class="tag r">Out of stock</span>`}${p.stock > 0 && p.stock <= 10 ? `<div class="low">Only ${p.stock} left!</div>` : ""}
  <a class="btn sm sec" href="#/product/${p.id}">Details</a></div></div>`;

let filters = { name: "", brand: "", min: "", max: "", cat: "" };
const ASSISTANT_HISTORY_KEY = "cr_assistant_history";
const MAX_ASSISTANT_TURNS = 10;
let assistantMessages = (() => {
    try {
        const stored = JSON.parse(sessionStorage.getItem(ASSISTANT_HISTORY_KEY) || "[]");
        return Array.isArray(stored) ? stored.slice(-MAX_ASSISTANT_TURNS * 2).map(message =>
            message.pending ? { ...message, pending: false, status: "Interrupted" } : message) : [];
    } catch { return []; }
})();
let assistantOpen = false;
let assistantAbortController = null;
let assistantLiveState = "Ready";
function assistantConversationMarkup() {
    if (!assistantMessages.length) {
        return `<div class="assistant-message assistant-message-bot"><p>Hi! Tell me what kind of gear you need, and I’ll find matching items from our catalogue.</p></div>`;
    }
    return assistantMessages.map(message => {
        const products = message.products || [];
        const productMarkup = products.length
            ? `${message.relaxedFilters?.length ? `<p class="assistant-alternative-note">Closest alternatives; relaxed: ${esc(message.relaxedFilters.join(", "))}. Price, stock, and waterproof requirements were kept.</p>` : ""}<div class="grid assistant-products">${products.map(card).join("")}</div>` : "";
        const cartData = message.cart;
        const cartMarkup = cartData
            ? `<div class="assistant-account-block"><b>Your cart</b>${cartData.items.length
                ? cartData.items.map(item => `<p class="assistant-account-row"><a href="#/product/${item.productId}">${esc(item.name)}</a><span>× ${item.quantity} · ${money(item.lineTotal)}</span></p>`).join("") +
                  `<p class="assistant-account-total">Total: ${money(cartData.total)}</p><a class="btn sm" href="#/cart">Go to cart</a>`
                : `<p class="assistant-account-row">Your cart is empty.</p>`}</div>`
            : "";
        const orders = message.orders || [];
        const ordersMarkup = orders.length
            ? `<div class="assistant-account-block">${orders.map(order => `
                <p class="assistant-account-row"><b>Order #${order.id}</b><span class="tag ${order.status === "CANCELLED" ? "r" : "g"}">${esc(order.status || "PENDING")}</span></p>
                ${order.items.map(item => `<p class="assistant-account-row"><a href="#/product/${item.productId}">${esc(item.name)}</a><span>× ${item.quantity} · ${money(Number(item.unitPrice) * Number(item.quantity))}</span></p>`).join("")}
                <p class="assistant-account-total">Total: ${money(order.total)}</p>`).join("")}
                <a class="btn sm" href="#/orders">View all orders</a></div>`
            : "";
        const source = message.source ? `<span class="assistant-source ${message.aiUsedForIntent ? "assistant-source-ai" : "assistant-source-local"}">${message.aiUsedForIntent ? (message.replyGeneratedByAi ? "Gemini AI" : "Gemini filters + catalogue reply") : "Local fallback; AI not used"}</span>${message.aiFallbackReason ? `<span class="assistant-source-note">AI issue: ${esc(message.aiFallbackReason)}</span>` : ""}` : "";
        const status = message.status ? `<span class="assistant-live-status">${esc(message.status)}</span>` : "";
        const pending = message.pending ? `<span class="assistant-typing" aria-label="Assistant is responding"><i></i><i></i><i></i></span>` : "";
        return `<article class="assistant-message ${message.role === "user" ? "assistant-message-user" : "assistant-message-bot"}">
            ${message.role === "assistant" ? `<span class="assistant-avatar" aria-hidden="true">CR</span>` : ""}
            <div class="assistant-bubble"><p>${esc(message.text)}</p>${status}${source}${pending}${productMarkup}${cartMarkup}${ordersMarkup}</div>
        </article>`;
    }).join("");
}
function renderAssistantConversation() {
    const conversation = $("#assistant-conversation");
    if (!conversation) return;
    conversation.innerHTML = assistantConversationMarkup();
    conversation.scrollTop = conversation.scrollHeight;
    try {
        const history = assistantMessages.filter(message => (message.role === "user" || message.role === "assistant")
            && !message.pending && !message.status && String(message.text || "").trim())
            .slice(-MAX_ASSISTANT_TURNS * 2)
            .map(message => ({ role: message.role, text: String(message.text || "").slice(0, 2000) }));
        sessionStorage.setItem(ASSISTANT_HISTORY_KEY, JSON.stringify(history));
    } catch { }
}
async function homePage() {
    if (user && user.role === "SUPPLIER") { location.hash = "#/manage"; return; }
    const cats = await api("/categories").catch(() => []);
    view(`<section class="hero"><div><h1>Gear up for every adventure.</h1><p>Waterproof watches, bags, shoes &amp; jackets — delivered fast.</p>
    <a class="btn" href="#list">Shop now</a></div><div class="hstats"><b>15+</b><span>Products</span><b>★ 4.5</b><span>Avg rating</span></div></section>
        <div class="assistant-widget">
            <button class="assistant-backdrop" id="assistant-backdrop" type="button" aria-label="Close shopping assistant" ${assistantOpen ? "" : "hidden"}></button>
            <section class="assistant-window" id="assistant-window" aria-labelledby="assistant-title" ${assistantOpen ? "" : "hidden"}>
                <header class="assistant-head"><div class="assistant-brand"><span class="assistant-avatar">CR</span><div><h2 id="assistant-title">Shopping assistant</h2><p class="assistant-header-state" id="assistant-header-state"><i></i><span>${esc(assistantLiveState)}</span></p></div></div><div class="assistant-actions"><button class="assistant-clear" id="assistant-clear" type="button" title="Clear chat">Clear</button><button class="assistant-close" id="assistant-close" type="button" aria-label="Close chat" title="Close chat"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div></header>
                <div class="assistant-conversation" id="assistant-conversation" role="log" aria-live="polite" aria-relevant="additions text">${assistantConversationMarkup()}</div>
                <form class="assistant-form" id="assistant-form"><textarea id="assistant-message" maxlength="1000" rows="1" placeholder="Describe what you need…" aria-label="Message the shopping assistant" required></textarea><button class="assistant-send" id="assistant-submit" type="submit" aria-label="Send message" title="Send message"><svg class="assistant-send-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg></button><button class="assistant-stop" id="assistant-stop" type="button" aria-label="Stop response" title="Stop response" hidden><span></span></button></form>
                <p class="assistant-footnote">Recommendations come from our catalogue.</p>
            </section>
            <button class="assistant-launcher" id="assistant-launcher" type="button" aria-controls="assistant-window" aria-expanded="${assistantOpen}" aria-label="Open shopping assistant" title="Open shopping assistant">
                <svg class="assistant-launcher-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5H6l-3 2v-6.5A7.5 7.5 0 1 1 20 11.5Z"/><path d="M8 11.5h.01M12 11.5h.01M16 11.5h.01"/></svg>
            </button>
        </div>
        <div class="chips">${cats.map(c => `<button class="chip ${filters.cat == c.id ? "on" : ""}" onclick="$('#fc').value=${c.id};$('#go').click()">${esc(c.name)}</button>`).join("")}</div>
    <h2 id="list">Products</h2><div class="bar">
    <input id="fn" placeholder="Search name…" value="${esc(filters.name)}">
    <input id="fb" placeholder="Brand" value="${esc(filters.brand)}">
    <input id="f1" type="number" placeholder="Min ₹" value="${filters.min}" style="max-width:110px">
    <input id="f2" type="number" placeholder="Max ₹" value="${filters.max}" style="max-width:110px">
    <select id="fc"><option value="">All categories</option>${cats.map(c => `<option value="${c.id}" ${filters.cat == c.id ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select>
    <select id="so"><option value="">Sort: Featured</option><option value="pa">Price: Low → High</option><option value="pd">Price: High → Low</option><option value="rt">Top rated</option></select><button class="btn" id="go">Filter</button><button class="btn sec" id="rs">Reset</button></div><div id="plist" class="grid"></div>`);
    const load = guard(async () => {
        filters = { name: $("#fn").value.trim(), brand: $("#fb").value.trim(), min: $("#f1").value, max: $("#f2").value, cat: $("#fc").value };
        const q = new URLSearchParams();
        ["name", "brand", "min", "max"].forEach(k => filters[k] && q.set(k, filters[k]));
        let ps = await api("/products" + (q.toString() ? "/filter?" + q : ""));
        const all = await api("/reviews").catch(() => []); ratings = {};
        all.forEach(r => { const k = r.product.id, x = ratings[k] || (ratings[k] = { sum: 0, n: 0 }); x.sum += r.rating; x.n++; x.avg = (x.sum / x.n).toFixed(1); });
        if (filters.cat) ps = ps.filter(p => p.category && p.category.id == filters.cat);
        const so = $("#so").value, rt = p => +(ratings[p.id] ? ratings[p.id].avg : 0);
        if (so === "pa") ps.sort((a, b) => a.price - b.price); if (so === "pd") ps.sort((a, b) => b.price - a.price); if (so === "rt") ps.sort((a, b) => rt(b) - rt(a));
        $("#plist").innerHTML = ps.length ? ps.map(card).join("") : `<div class="empty" style="grid-column:1/-1">No products found</div>`;
    });
    $("#so").onchange = load;
    $("#go").onclick = load;
    $("#rs").onclick = () => { filters = { name: "", brand: "", min: "", max: "", cat: "" }; homePage(); };
    $("#fn").onkeydown = e => e.key === "Enter" && load();
    const toggleAssistant = open => {
        if (!open && assistantAbortController) assistantAbortController.abort();
        assistantOpen = open;
        const window = $("#assistant-window"), launcher = $("#assistant-launcher");
        window.hidden = !open;
        $("#assistant-backdrop").hidden = !open;
        launcher.setAttribute("aria-expanded", String(open));
        if (open) $("#assistant-message").focus();
    };
    $("#assistant-launcher").onclick = () => toggleAssistant(true);
    $("#assistant-close").onclick = () => toggleAssistant(false);
    $("#assistant-backdrop").onclick = () => toggleAssistant(false);
    $("#assistant-clear").onclick = () => {
        if (assistantAbortController) assistantAbortController.abort();
        assistantAbortController = null;
        assistantLiveState = "Ready";
        $("#assistant-header-state span").textContent = assistantLiveState;
        assistantMessages = [];
        sessionStorage.removeItem(ASSISTANT_HISTORY_KEY);
        renderAssistantConversation();
    };
    $("#assistant-message").onkeydown = event => {
        if (event.key === "Escape") toggleAssistant(false);
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            $("#assistant-form").requestSubmit();
        }
    };
    $("#assistant-stop").onclick = () => assistantAbortController?.abort();
    $("#assistant-form").onsubmit = async event => {
        event.preventDefault();
        const button = $("#assistant-submit"), input = $("#assistant-message");
        const message = input.value.trim();
        if (!message) return;
        assistantMessages.push({ role: "user", text: message });
        const boundedHistory = assistantMessages.slice(0, -1).slice(-MAX_ASSISTANT_TURNS * 2)
            .filter(turn => (turn.role === "user" || turn.role === "assistant")
                && !turn.pending && !turn.status && String(turn.text || "").trim())
            .map(turn => ({ role: turn.role, content: String(turn.text || "").slice(0, 2000) }));
        const assistantIndex = assistantMessages.length;
        assistantMessages.push({ role: "assistant", text: "", status: "Understanding your message", pending: true });
        input.value = "";
        button.disabled = true;
        $("#assistant-stop").hidden = false;
        assistantAbortController = new AbortController();
        assistantLiveState = "Connecting";
        $("#assistant-header-state span").textContent = assistantLiveState;
        $("#assistant-header-state").className = "assistant-header-state is-active";
        renderAssistantConversation();
        try {
            const response = await fetch(API_BASE + "/assistant/chat/stream", {
                method: "POST", headers: { "Content-Type": "application/json", "Accept": "text/event-stream" },
                body: JSON.stringify({ message, history: boundedHistory, userId: user ? user.id : null }), signal: assistantAbortController.signal
            });
            if (!response.ok || !response.body) throw new Error(`Assistant request failed (${response.status})`);
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";
            let finished = false;
            const handleEvent = eventBlock => {
                let eventName = "message", dataText = "";
                for (const line of eventBlock.split("\n")) {
                    if (line.startsWith("event:")) eventName = line.slice(6).trim();
                    else if (line.startsWith("data:")) dataText += line.slice(5).trim();
                }
                if (!dataText) return;
                const payload = JSON.parse(dataText);
                const current = assistantMessages[assistantIndex];
                if (eventName === "status") {
                    current.status = payload.text || "Thinking";
                    assistantLiveState = payload.text || "Thinking";
                    $("#assistant-header-state span").textContent = assistantLiveState;
                } else if (eventName === "token") {
                    current.text += payload.text || "";
                    current.status = "";
                    assistantLiveState = "Streaming";
                    $("#assistant-header-state span").textContent = assistantLiveState;
                } else if (eventName === "final") {
                    const result = payload.response || {};
                    current.text = result.reply || current.text;
                    current.products = result.products || [];
                    current.source = result.source;
                    current.aiUsedForIntent = result.aiUsedForIntent;
                    current.replyGeneratedByAi = result.replyGeneratedByAi;
                    current.aiFallbackReason = result.aiFallbackReason;
                    current.relaxedFilters = result.relaxedFilters || [];
                    current.cart = result.cart || null;
                    current.orders = result.orders || [];
                    current.intent = payload.intent;
                    current.status = "";
                    current.pending = false;
                    assistantLiveState = result.source === "FALLBACK" ? "Using local fallback" : "Ready";
                    $("#assistant-header-state span").textContent = assistantLiveState;
                    $("#assistant-header-state").className = `assistant-header-state ${result.source === "FALLBACK" ? "is-fallback" : ""}`;
                    current.products.forEach(product => {
                        ratings[product.id] = { avg: Number(product.averageRating).toFixed(1), n: product.ratingCount };
                    });
                    finished = true;
                } else if (eventName === "clarification") {
                    current.status = "A quick follow-up";
                } else if (eventName === "error") {
                    throw new Error(payload.text || "Assistant stream failed");
                }
                renderAssistantConversation();
            };
            while (true) {
                const { value, done } = await reader.read();
                buffer += decoder.decode(value || new Uint8Array(), { stream: !done }).replace(/\r\n/g, "\n");
                let boundary;
                while ((boundary = buffer.indexOf("\n\n")) >= 0) {
                    const block = buffer.slice(0, boundary);
                    buffer = buffer.slice(boundary + 2);
                    handleEvent(block);
                }
                if (done) break;
            }
            if (buffer.trim()) handleEvent(buffer);
            if (!finished) throw new Error("Assistant stream ended before the final response");
        } catch (error) {
            const current = assistantMessages[assistantIndex];
            if (!current) return;
            current.status = error.name === "AbortError" ? "Response stopped" : "Connection issue";
            current.pending = false;
            assistantLiveState = error.name === "AbortError" ? "Ready" : "Connection issue";
            $("#assistant-header-state span").textContent = assistantLiveState;
            $("#assistant-header-state").className = `assistant-header-state ${error.name === "AbortError" ? "" : "is-error"}`;
            if (!current.text) current.text = error.name === "AbortError" ? "Stopped generating." : (error.message || "I couldn’t reach the assistant. Please try again.");
        } finally {
            assistantAbortController = null;
            if (button.disabled) button.disabled = false;
            button.disabled = false;
            $("#assistant-stop").hidden = true;
            renderAssistantConversation();
            input.focus();
        }
    };
    await load();
}

async function productPage(id) {
    const p = await api(`/products/${id}`);
    if (!p) return view(`<div class="empty">Product not found</div>`);
    const draw = async () => {
        const rv = await api(`/reviews/product/${id}`);
        const avg = rv.length ? (rv.reduce((s, r) => s + r.rating, 0) / rv.length).toFixed(1) : null;
        view(`<a href="#/">← Back</a><div class="panel two" style="margin-top:12px">${img(p, "big")}<div>
      <h1>${esc(p.name)}</h1><div class="mu">${esc(p.brand || "")}</div>
      <p>${p.category ? `<span class="tag">${esc(p.category.name)}</span>` : ""}${p.waterproof ? `<span class="tag g">Waterproof</span>` : ""}
      ${avg ? `<span class="stars">★ ${avg}</span> <span class="mu">(${rv.length})</span>` : ""}</p>
      <p>${esc(p.description || "No description.")}</p><div class="price">${money(p.price)}</div>
      <p class="mu">${p.stock > 0 ? p.stock + " in stock" : "Out of stock"}</p>
      <div class="qty"><input id="q" type="number" min="1" max="${p.stock}" value="1" style="width:80px">
      <button class="btn" ${p.stock > 0 ? "" : "disabled"} onclick="addToCart(${p.id}, Math.max(1, +$('#q').value))">Add to cart</button></div></div></div>
      <div class="panel"><h2>Reviews</h2>${rv.length ? rv.map(r => `<p><span class="stars">${"★".repeat(r.rating)}${"☆".repeat(5 - r.rating)}</span>
      <b>${esc(r.user && r.user.name)}</b><br>${esc(r.comment)}${user && r.user && r.user.id === user.id ? ` <button class="btn red sm" onclick="delReview(${r.id})">Delete</button>` : ""}</p>`).join("") : `<p class="mu">No reviews yet.</p>`}
      ${user ? `<hr><form class="f" id="rf"><select id="rr">${[5, 4, 3, 2, 1].map(n => `<option value="${n}">${n} ★</option>`).join("")}</select>
      <textarea id="rc" rows="3" placeholder="Write a review…" required></textarea><button class="btn">Submit review</button></form>` : `<p class="mu"><a href="#/login">Log in</a> to write a review.</p>`}</div>`);
        const f = $("#rf");
        if (f) f.onsubmit = guard(async e => {
            e.preventDefault();
            await api(`/reviews/user/${user.id}/product/${id}?rating=${$("#rr").value}&comment=${encodeURIComponent($("#rc").value)}`, { method: "POST" });
            toast("Review added"); draw();
        });
    };
    window.delReview = guard(async rid => { await api(`/reviews/${rid}?userId=${user.id}`, { method: "DELETE" }); toast("Review deleted"); draw(); });
    await draw();
}

async function cartPage() {
    if (!user) return (location.hash = "#/login");
    if (user.role === "SUPPLIER") { toast("Cart is available to customer accounts only.", true); location.hash = "#/manage"; return; }
    const draw = async () => {
        const items = await cartItems();
        if (!items.length) return view(`<h1>Your Cart</h1><div class="empty">Your cart is empty 🛒<br><br><a class="btn" href="#/">Start shopping</a></div>`);
        const total = items.reduce((s, i) => s + i.product.price * i.quantity, 0);
        view(`<h1>Your Cart</h1><div class="panel tablewrap"><table><tr><th>Product</th><th>Price</th><th>Qty</th><th>Subtotal</th><th></th></tr>
      ${items.map(i => `<tr><td><div class="row">${img(i.product, "thumb")}<a href="#/product/${i.product.id}">${esc(i.product.name)}</a></div></td><td>${money(i.product.price)}</td>
      <td><div class="qty"><button class="btn sec sm" onclick="setQty(${i.id},${i.quantity - 1})">−</button>${i.quantity}
      <button class="btn sec sm" onclick="setQty(${i.id},${i.quantity + 1})">+</button></div></td><td>${money(i.product.price * i.quantity)}</td>
      <td><button class="btn red sm" onclick="rmItem(${i.id})">Remove</button></td></tr>`).join("")}</table>
      <p style="text-align:right;font-size:20px"><b>Total: ${money(total)}</b></p>
      <p style="text-align:right"><button class="btn" onclick="checkout()">Place order</button></p></div>`);
    };
    window.setQty = guard(async (id, q) => { if (q < 1) return rmItem(id); await api(`/cart-items/${id}?quantity=${q}`, { method: "PUT" }); draw(); });
    window.rmItem = guard(async id => { await api(`/cart-items/${id}`, { method: "DELETE" }); draw(); });
    window.checkout = guard(async () => { await api(`/orders/checkout/${user.id}`, { method: "POST" }); await cartItems(); toast("Order placed 🎉"); location.hash = "#/orders"; });
    await draw();
}

async function ordersPage() {
    if (!user) return (location.hash = "#/login");
    if (user.role === "SUPPLIER") { toast("Supplier order management is not available on this page yet.", true); location.hash = "#/manage"; return; }
    const orders = (await api(`/orders/user/${user.id}`)).reverse();
    const withItems = await Promise.all(orders.map(async o => ({ ...o, items: await api(`/order-items/order/${o.id}`) })));
    view(`<h1>My Orders</h1>` + (withItems.length ? withItems.map(o => `<div class="panel"><div style="display:flex;justify-content:space-between">
    <b>Order #${o.id}</b><span class="tag ${o.status === "CANCELLED" ? "r" : "g"}">${esc(o.status)}</span></div>
    ${o.items.map(i => `<p class="row">${img(i.product, "thumb")}<span>${esc(i.product.name)} × ${i.quantity}</span> <span class="mu">— ${money(i.product.price * i.quantity)}</span></p>`).join("")}
    <b>Total: ${money(o.items.reduce((s, i) => s + i.product.price * i.quantity, 0))}</b></div>`).join("") : `<div class="empty">No orders yet</div>`));
}

async function supplierOrdersPage() {
    if (!user) return (location.hash = "#/login");
    if (user.role !== "SUPPLIER") { toast("Supplier accounts only.", true); return (location.hash = "#/manage"); }
    const orders = await api(`/supplier-portal/${user.id}/orders`);
    view(`<h1>Supplier Orders</h1><p class="mu">Orders that contain products supplied by your account.</p>` +
      (orders.length ? orders.map(o => `<div class="panel" style="margin-bottom:16px">
        <div style="display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;align-items:center">
          <div><h2 style="margin:0">Order #${o.id}</h2><p class="mu">Customer: ${esc(o.customer || "Customer")} · ${o.createdAt ? new Date(o.createdAt).toLocaleString() : "Date unavailable"}</p></div>
          <span class="tag ${o.status === "CANCELLED" ? "r" : "g"}">${esc(o.status || "PENDING")}</span>
        </div>
        ${o.items.map(i => `<p class="row"><span>${esc(i.name)} × ${i.quantity}</span><span class="mu">${money(Number(i.price) * Number(i.quantity))}</span></p>`).join("")}
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:12px">
          <label for="supplier-status-${o.id}">Update order status</label>
          <select id="supplier-status-${o.id}"><option ${o.status === "PROCESSING" ? "selected" : ""}>PROCESSING</option><option ${o.status === "SHIPPED" ? "selected" : ""}>SHIPPED</option><option ${o.status === "DELIVERED" ? "selected" : ""}>DELIVERED</option><option ${o.status === "CANCELLED" ? "selected" : ""}>CANCELLED</option></select>
          <button class="btn sm" onclick="updateSupplierOrderStatus(${o.id})">Save status</button>
        </div>
      </div>`).join("") : `<div class="empty">No orders containing your products yet.</div>`));
}
window.updateSupplierOrderStatus = guard(async id => {
    const status = document.getElementById(`supplier-status-${id}`).value;
    await api(`/supplier-portal/${user.id}/orders/${id}/status?status=${encodeURIComponent(status)}`, { method: "PUT" });
    toast("Order status updated");
    await supplierOrdersPage();
});

function loginPage() {
    view(`<div class="panel" style="max-width:460px;margin:auto"><h1>Login</h1><form class="f" id="lf">
    <input id="em" type="email" placeholder="Email" required><input id="pw" type="password" placeholder="Password" required>
    <button class="btn">Login</button></form><p class="mu">New here? <a href="#/register">Create an account</a></p></div>`);
    $("#lf").onsubmit = guard(async e => {
        e.preventDefault();
        user = await api("/auth/login", { json: { email: $("#em").value, password: $("#pw").value } });
        localStorage.setItem("cr_user", JSON.stringify(user)); cartObj = null;
        renderNav(); if (user.role !== "SUPPLIER") cartItems().catch(() => {}); toast(`Welcome, ${user.name}!`); location.hash = user.role === "SUPPLIER" ? "#/manage" : "#/";
    });
}

function registerPage() {
    let sup = false;
    const draw = () => {
        view(`<div class="panel" style="max-width:460px;margin:auto"><h1>Sign up</h1>
      <div class="tabs"><button class="${sup ? "" : "on"}" id="t1">Customer</button><button class="${sup ? "on" : ""}" id="t2">Supplier</button></div>
      <form class="f" id="rf"><input id="nm" placeholder="Full name" required><input id="em" type="email" placeholder="Email" required>
      <input id="pw" type="password" placeholder="Password" required>
      ${sup ? `<input id="bn" placeholder="Business name" required><input id="ph" placeholder="Phone" required><input id="ad" placeholder="Address" required>` : ""}
      <button class="btn">Create account</button></form></div>`);
        $("#t1").onclick = () => { sup = false; draw(); }; $("#t2").onclick = () => { sup = true; draw(); };
        $("#rf").onsubmit = guard(async e => {
            e.preventDefault();
            const u = { name: $("#nm").value, email: $("#em").value, password: $("#pw").value };
            if (sup) await api("/auth/register/supplier", { json: { user: u, businessName: $("#bn").value, phone: $("#ph").value, address: $("#ad").value } });
            else await api("/auth/register/customer", { json: u });
            toast("Account created — please log in"); location.hash = "#/login";
        });
    };
    draw();
}

/* ===== admin / supplier ===== */
async function managePage() {
    if (!isManager()) return (location.hash = "#/");
    const admin = user.role === "ADMIN";
    let tab = admin ? "dashboard" : (location.hash === "#/supplier-products" ? "products" : "dashboard");
    let analytics = null;

    const draw = async () => {
        const tabs = admin
            ? ["dashboard", "products", "categories", "orders", "users", "suppliers", "reviews", "fraud"]
            : ["dashboard", "products"];

        let body = "";

        if (tab === "dashboard" && admin) {
            const [stats, ps, an] = await Promise.all([
                api("/admin/dashboard"),
                api("/admin/products"),
                api("/admin/analytics").catch(() => null)
            ]);
            analytics = an;

            const rev = stats.revenue || 0;

            body = `<div class="stats">${[
                ["Revenue", money(rev)],
                ["Orders", stats.orders],
                ["Products", stats.products],
                ["Users", stats.users],
                ["Reviews", stats.reviews],
                ["Low Stock", stats.lowStock]
            ].map(([k, v]) =>
                `<div class="stat"><span class="mu">${k}</span><b>${v}</b></div>`
            ).join("")}</div>
            ${analyticsHtml(analytics)}
            <div class="panel">
    <h2>Low Stock Products</h2>
    ${
                ps.filter(p => p.stock <= 10).length
                    ? ps.filter(p => p.stock <= 10).map(p => `
            <div class="low-stock-row">
                <div>
                    <b>${esc(p.name)}</b>
                    <span class="mu">Stock remaining</span>
                </div>
                <span class="tag r">${p.stock}</span>
            </div>
        `).join("")
                    : `<div class="empty">All products have healthy stock levels 🎉</div>`
            }
</div>`;

        } else if (tab === "dashboard" && !admin) {
            const ps = await api(`/supplier-portal/${user.id}/products`);
            const productCount = ps.length;
            const stockCount = ps.reduce((sum, p) => sum + Number(p.stock || 0), 0);
            const inventoryValue = ps.reduce((sum, p) => sum + Number(p.price || 0) * Number(p.stock || 0), 0);
            const lowStock = ps.filter(p => Number(p.stock || 0) <= 10);
            body = `<div class="stats">${[
                ["My Products", productCount],
                ["Total Units in Stock", stockCount],
                ["Inventory Value", money(inventoryValue)],
                ["Low-stock Products", lowStock.length]
            ].map(([k, v]) => `<div class="stat"><span class="mu">${k}</span><b>${v}</b></div>`).join("")}</div>
            <div class="panel"><h2>Inventory overview</h2>
            ${lowStock.length ? lowStock.map(p => `<div class="low-stock-row"><div><b>${esc(p.name)}</b><span class="mu">Low stock — consider restocking</span></div><span class="tag r">${Number(p.stock || 0)} left</span></div>`).join("") : `<div class="empty">No low-stock products. Your inventory looks good!</div>`}
            <p><button class="btn" onclick="location.hash='#/supplier-products'">Manage my products</button></p></div>`;

        } else if (tab === "products") {
            const productPath = admin ? "/products" : `/supplier-portal/${user.id}/products`;
            const [ps, cats] = await Promise.all([
                api(productPath),
                api("/categories").catch(() => [])
            ]);

            body = `${!admin ? `<div class="panel"><h2 id="ft">Add product</h2><p class="mu">Products you add here belong to your supplier account.</p><form class="f wide" id="pf">
                <input type="hidden" id="pid">
                <input id="pn" placeholder="Name" required>
                <input id="pb" placeholder="Brand">
                <input id="pp" type="number" step="0.01" placeholder="Price" required>
                <input id="ps" type="number" placeholder="Stock" required>
                <select id="pc">
                    <option value="">No category</option>
                    ${cats.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join("")}
                </select>
                <select id="pw">
                    <option value="false">Not waterproof</option>
                    <option value="true">Waterproof</option>
                </select>
                <input id="pd" placeholder="Description" style="grid-column:1/-1">
                <input id="pi" placeholder="Image URL (https://…)" style="grid-column:1/-1">
                <button class="btn">Save product</button>
                <button type="button" class="btn sec" onclick="resetProductForm()">Clear</button>
            </form></div>` : `<div class="panel"><h2>Product management</h2><p class="mu">View products from all suppliers and manage listings here. Suppliers add and maintain their own products.</p></div>`}

            <div class="panel tablewrap"><table>
                <tr>
                    <th>ID</th>
                    <th>Name</th>
                    <th>Brand</th>
                    ${admin ? "<th>Supplier</th>" : ""}
                    <th>Price</th>
                    <th>Stock</th>
                    <th></th>
                </tr>

                ${ps.map(p => `<tr>
                    <td>${p.id}</td>
                    <td>${esc(p.name)}</td>
                    <td>${esc(p.brand)}</td>
                    ${admin ? `<td>${esc(p.supplier?.businessName || "Not assigned")}</td>` : ""}
                    <td>${money(p.price)}</td>
                    <td>${p.stock}</td>
                    <td>
                        ${admin ? `<button class="btn red sm" onclick="delP(${p.id})">Remove</button>` : `<button class="btn sec sm" onclick='editP(${JSON.stringify(p).replace(/'/g, "&#39;")})'>Edit</button>
                        <button class="btn red sm" onclick="delP(${p.id})">Delete</button>`}
                    </td>
                </tr>`).join("")}
            </table></div>`;

        } else if (tab === "categories") {
            const cats = await api("/categories");

            body = `<div class="panel">
                <form class="f" id="cf">
                    <input id="cn" placeholder="New category name" required>
                    <button class="btn">Add category</button>
                </form>
            </div>

            <div class="panel">
                ${cats.map(c => `<span class="tag">${c.id}: ${esc(c.name)}</span>`).join("") || "No categories"}
            </div>`;

        } else if (tab === "orders") {
                const os = (await api("/admin/orders")).reverse();

                body = `
        <div class="panel tablewrap">
            <table>
                <tr>
                    <th>ID</th>
                    <th>Customer</th>
                    <th>Date</th>
                    <th>Status</th>
                    <th>Update</th>
                </tr>

                ${os.map(o => `<tr>
                    <td>#${o.id}</td>
                    <td>${esc(o.user && o.user.name)}</td>
                    <td>${o.createdAt ? new Date(o.createdAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "—"}</td>
                    <td>${esc(o.status)}</td>
                    <td>
                        <select onchange="setSt(${o.id},this.value)">
                            ${["PLACED", "SHIPPED", "DELIVERED", "CANCELLED"]
                    .map(s => `<option ${s === o.status ? "selected" : ""}>${s}</option>`)
                    .join("")}
                        </select>
                    </td>
                </tr>`).join("")}
            </table>
        </div>`;
            } else if (tab === "users") {
            const us = await api("/admin/users");

            body = `<div class="panel tablewrap"><table>
                <tr>
                    <th>ID</th>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th></th>
                </tr>

                ${us.map(u => `<tr>
                    <td>${u.id}</td>
                    <td>${esc(u.name)}</td>
                    <td>${esc(u.email)}</td>
                    <td>${esc(u.role)}</td>
                    <td>
                        ${u.id !== user.id
                ? `<button class="btn red sm" onclick="delU(${u.id})">Delete</button>`
                : ""}
                    </td>
                </tr>`).join("")}
            </table></div>`;
        }

        else if (tab === "suppliers") {
            const suppliers = await api("/admin/suppliers");

            body = `<div class="panel tablewrap">
        <h2>Suppliers</h2>
        <table>
            <tr>
                <th>ID</th>
                <th>Business Name</th>
                <th>Phone</th>
                <th>Address</th>
                <th>User</th>
                <th></th>
            </tr>
            ${suppliers.map(s => `<tr>
                <td>${s.id}</td>
                <td>${esc(s.businessName)}</td>
                <td>${esc(s.phone)}</td>
                <td>${esc(s.address)}</td>
                <td>${esc(s.user && s.user.name)}</td>
                <td>
                    <button class="btn red sm" onclick="delSupplier(${s.id})">
                        Delete
                    </button>
                </td>
            </tr>`).join("")}
        </table>
    </div>`;
        } else if (tab === "reviews") {
            const reviews = await api("/admin/reviews");

            body = `<div class="panel tablewrap">
        <h2>Reviews</h2>
        <table>
            <tr>
                <th>ID</th>
                <th>Customer</th>
                <th>Product</th>
                <th>Rating</th>
                <th>Comment</th>
                <th></th>
            </tr>

            ${reviews.map(r => `<tr>
                <td>${r.id}</td>
                <td>${esc(r.user && r.user.name || "")}</td>
                <td>${esc(r.product && r.product.name || "")}</td>
                <td>${"★".repeat(r.rating || 0)}</td>
                <td>${esc(r.comment || "")}</td>
                <td>
                    <button class="btn red sm"
                        onclick="delReview(${r.id})">
                        Delete
                    </button>
                </td>
            </tr>`).join("")}
        </table>
    </div>`;
        } else if (tab === "fraud") {
        const [fraudResults, suppliers] = await Promise.all([
            api("/admin/fraud/suppliers"),
            api("/admin/suppliers")
        ]);

        fraudResults.sort((a, b) => b.fraudScore - a.fraudScore);
            const fraudReportTime = new Date().toLocaleString();
            const highRiskCount = fraudResults.filter(
                f => f.status === "HIGH_RISK"
            ).length;

            const suspiciousCount = fraudResults.filter(
                f => f.status === "SUSPICIOUS"
            ).length;

            const normalCount = fraudResults.filter(
                f => f.status === "NORMAL"
            ).length;
        const supplierById = Object.fromEntries(
            suppliers.map(s => [s.id, s])
        );

            body = `
<WritingBlock id="58321" variant="standard"> <div class="panel" style="margin-bottom:16px;"> <label for="fraudStatusFilter">Filter by risk status</label> <select id="fraudStatusFilter" onchange="filterFraudSuppliers(this.value)"> <option value="ALL">All Suppliers</option> <option value="NORMAL">Normal</option> <option value="SUSPICIOUS">Suspicious</option> <option value="HIGH_RISK">High Risk</option> </select> </div></WritingBlock>
    <div class="grid" style="grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:20px;">
        <div class="panel">
            <h3>Normal Suppliers</h3>
            <h2>${normalCount}</h2>
        </div>
        <div class="panel">
            <h3>Suspicious Suppliers</h3>
            <h2>${suspiciousCount}</h2>
        </div>
        <div class="panel">
            <h3>High-Risk Suppliers</h3>
            <h2>${highRiskCount}</h2>
        </div>
    </div>

    <div class="panel tablewrap">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;">
    <h2>Supplier Fraud Detection</h2>
    <button onclick="exportFraudReport()">
    Export CSV
</button>

<p class="mu">
    Fraud scores are automated risk indicators based on review patterns.
    Investigate flagged suppliers before taking action; a high score alone
    does not establish fraud.
</p>

<div class="fraud-legend">
    <span class="tag g">0–49: Normal</span>
    <span class="tag warning">50–69: Suspicious</span>
    <span class="tag r">70–100: High Risk</span>
</div>
    <button onclick="managePage('fraud')">Refresh Results</button>
    <p class="mu">Report generated: ${fraudReportTime}</p>
</div>
            <p class="mu">
                Review-based risk indicators for admin investigation.
                A high score is not proof of fraud.
            </p>

            <table id="fraudTable">
                <tr>
                    <th>Supplier</th>
                    <th>Reviews</th>
                    <th>Avg Rating</th>
                    <th>Low Ratings</th>
                    <th>Risk Score</th>
                    <th>Status</th>
                    <th>Reasons</th>
                    <th>Products</th>
                    <th>Details</th>
                </tr>

                ${fraudResults.map(f => {
            const supplier = supplierById[f.supplierId];
            const name = supplier
                ? supplier.businessName
                : `Supplier #${f.supplierId}`;


            const statusClass =
                f.status === "NORMAL" ? "g" :
                    f.status === "SUSPICIOUS" ? "warning" : "r";

                const riskWarning = f.status === "HIGH_RISK"
                    ? "Investigate this supplier"
                    : f.status === "SUSPICIOUS"
                        ? "Review supplier activity"
                        : "No urgent action";

            return `
                        <tr data-status="${esc(f.status)}">
                            <td>${esc(name)}</td>
                            <td>${f.totalReviews}</td>
                            <td>${f.averageRating}</td>
                            <td>${f.lowRatingCount}
                                (${f.lowRatingPercentage}%)
                            </td>
                        <td>
                        <span class="tag ${
                f.fraudScore >= 70 ? "r" :
                    f.fraudScore >= 50 ? "warning" : "g"
            }">
        ${f.fraudScore}/100
    </span>
</td>
                            <td>
    <span class="tag ${statusClass}">${esc(f.status)}</span>
    <p class="mu">${esc(riskWarning)}</p>
</td>
                            <td>${esc((f.reasons || []).join(", ") || "No risk indicators")}</td>
                            
<td>
    <button onclick="viewSupplierFraud(${f.supplierId})">
        View Details
    </button>
</td>
<td>
    ${(f.productDetails || []).map(p => `
        <div>
            <strong>${esc(p.productName)}</strong><br>
            Reviews: ${p.reviewCount}<br>
            Average: ${p.averageRating}
        </div>
    `).join("<hr>") || "No product details"}
</td>
                        </tr>
                    `;
        }).join("")}
            </table>
        </div>
    `;}
        view(`
            <div class="admin-heading">
    <div>
        <h1>${admin ? "Admin Dashboard" : (tab === "dashboard" ? "Supplier Dashboard" : "My Products")}</h1>
        <p>${admin ? "Manage your store, products, orders and customers." : (tab === "dashboard" ? "Overview of your products and inventory." : "Add and manage products belonging to your supplier account.")}</p>
    </div>
</div>
            <div class="tabs">
                ${tabs.map(t =>
            `<button class="${t === tab ? "on" : ""}" onclick="tabTo('${t}')">
                        ${t[0].toUpperCase() + t.slice(1)}
                    </button>`
        ).join("")}
            </div>
            ${body}
        `);

        if (tab === "dashboard" && analytics) drawCharts(analytics);

        const pf = $("#pf");
        const cf = $("#cf");

        if (pf) {
            pf.onsubmit = guard(async e => {
                e.preventDefault();

                const id = $("#pid").value;
                const cid = $("#pc").value;

                const p = {
                    name: $("#pn").value,
                    brand: $("#pb").value,
                    price: +$("#pp").value,
                    stock: +$("#ps").value,
                    description: $("#pd").value,
                    imageUrl: $("#pi").value,
                    waterproof: $("#pw").value === "true",
                    category: cid ? { id: +cid } : null
                };

                const endpoint = admin ? "/products" : `/supplier-portal/${user.id}/products`;
                await api(
                    id ? `${endpoint}/${id}` : endpoint,
                    { json: p, method: id ? "PUT" : "POST" }
                );

                toast("Product saved");
                draw();
            });
        }

        if (cf) {
            cf.onsubmit = guard(async e => {
                e.preventDefault();

                await api("/categories", {
                    json: {
                        name: $("#cn").value
                    }
                });

                toast("Category added");
                draw();
            });
        }
    };

    window.tabTo = t => {
        tab = t;
        if (!admin) location.hash = t === "products" ? "#/supplier-products" : "#/supplier-dashboard";
        draw().catch(e => toast(e.message, true));
    };

    window.resetProductForm = () => {
        const form = $("#pf");
        if (form) form.reset();
        if ($("#pid")) $("#pid").value = "";
        if ($("#ft")) $("#ft").textContent = "Add product";
    };

    window.editP = p => {
        if (admin) return;
        $("#ft").textContent = "Edit product #" + p.id;
        $("#pid").value = p.id;
        $("#pn").value = p.name || "";
        $("#pb").value = p.brand || "";
        $("#pp").value = p.price;
        $("#ps").value = p.stock;
        $("#pd").value = p.description || "";
        $("#pi").value = p.imageUrl || "";
        $("#pw").value = String(!!p.waterproof);
        $("#pc").value = p.category ? p.category.id : "";

        window.scrollTo(0, 0);
    };

    window.delP = guard(async id => {
        if (confirm("Delete this product?")) {
            const endpoint = admin ? `/products/${id}` : `/supplier-portal/${user.id}/products/${id}`;
            await api(endpoint, { method: "DELETE" });
            draw();
        }
    });

    window.delU = guard(async id => {
        if (confirm("Delete this user?")) {
            await api(`/admin/users/${id}`, {
                method: "DELETE"
            });
            draw();
        }
    });

    window.delSupplier = guard(async id => {
        if (confirm("Delete this supplier?")) {
            await api(`/admin/suppliers/${id}`, { method: "DELETE" });
            toast("Supplier deleted");
            draw();
        }
    });

    window.delReview = guard(async id => {
        if (confirm("Delete this review?")) {
            await api(`/admin/reviews/${id}`, { method: "DELETE" });
            toast("Review deleted");
            draw();
        }
    });

    window.setSt = guard(async (id, s) => {
        await api(`/admin/orders/${id}/status?status=${s}`, {
            method: "PUT"
        });

        toast("Status updated");
    });

    await draw();
}

function filterFraudSuppliers(status) {
    const rows = document.querySelectorAll(
        "#fraudTable tr[data-status]"
    );

    rows.forEach(row => {
        const matches =
            status === "ALL" ||
            row.dataset.status === status;

        row.style.display = matches ? "" : "none";
    });
}



function exportFraudReport() {
    const confirmed = confirm(
        "Export the currently displayed supplier fraud report as a CSV file?"
    );

    if (!confirmed) {
        return;
    }

    const rows = document.querySelectorAll(
        "#fraudTable tr[data-status]"
    );

    if (rows.length === 0) {
        alert("No supplier records available to export.");
        return;
    }

    const headers = [
        "Supplier",
        "Reviews",
        "Average Rating",
        "Low Ratings",
        "Risk Score",
        "Status",
        "Reasons"
    ];

    const csvEscape = value =>
        '"' + String(value ?? "").replace(/"/g, '""') + '"';

    const data = Array.from(rows, row => {
        const cells = row.querySelectorAll("td");

        return [
            cells[0]?.innerText.trim(),
            cells[1]?.innerText.trim(),
            cells[2]?.innerText.trim(),
            cells[3]?.innerText.trim(),
            cells[4]?.innerText.trim(),
            cells[5]?.innerText.trim(),
            cells[6]?.innerText.trim()
        ];
    });

    const csv = [headers, ...data]
        .map(row => row.map(csvEscape).join(","))
        .join("\r\n");

    const blob = new Blob(
        ["\uFEFF" + csv],
        { type: "text/csv;charset=utf-8;" }
    );

    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.href = url;
    const timestamp = new Date()
        .toISOString()
        .replace(/[:.]/g, "-");

    link.download = `cart-rush-fraud-report-${timestamp}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function viewSupplierFraud(supplierId) {
    try {
        const report = await api(
            `/admin/fraud/suppliers/${supplierId}`
        );



        const productText = (report.productDetails || []).map(p =>
            `${p.productName} — Reviews: ${p.reviewCount}, Average: ${p.averageRating}`
        ).join("\n");

        const reportText =
            `Supplier Fraud Report\n\n` +
            `Supplier ID: ${report.supplierId}\n` +
            `Risk Score: ${report.fraudScore}/100\n` +
            `Status: ${report.status}\n` +
            `Total Reviews: ${report.totalReviews}\n` +
            `Average Rating: ${report.averageRating}\n\n` +
            `Reasons:\n${(report.reasons || []).join("\n") || "No risk indicators"}\n\n` +
            `Product Details:\n${productText || "No product details"}`;

        try {
            await navigator.clipboard.writeText(reportText);
            alert("Fraud report copied to clipboard.");
        } catch (error) {
            alert(reportText);
        }
    } catch (error) {
        alert("Could not load the supplier fraud report.");
        console.error(error);
    }
}
/* ===== dashboard analytics (Chart.js) ===== */
const chartInstances = [];
const compactNum = new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 });
const rupeeShort = v => "₹" + compactNum.format(v);
const SPARK_COLORS = ["#3b5bdb", "#f59f00", "#e03131", "#2f9e44"];
const STATUS_COLORS = { PLACED: "#3b5bdb", SHIPPED: "#f59f00", DELIVERED: "#2f9e44", CANCELLED: "#e03131" };
const PALETTE = ["#4f46e5", "#f59f00", "#16a34a", "#e03131", "#0ea5e9", "#9333ea", "#64748b"];

function analyticsHtml(a) {
    if (!a) return `<div class="panel"><div class="empty">Could not load analytics.<br><small>Check that the backend exposes /api/admin/analytics.</small></div></div>`;
    const fmt = { revenue: money, aov: money, orders: n => Number(n).toLocaleString("en-IN"), units: n => Number(n).toLocaleString("en-IN") };
    const delta = c => c == null
        ? `<span class="delta flat">no prior data</span>`
        : `<span class="delta ${c >= 0 ? "up" : "down"}">${c >= 0 ? "▲" : "▼"} ${Math.abs(c).toFixed(1)}%</span>`;
    const box = (id, title, extra = "") => `<div class="panel chart-panel"><div class="chart-head"><h2>${title}</h2>${extra}</div><div class="cwrap" id="w-${id}"><canvas id="${id}"></canvas></div></div>`;
    return `
    <div class="kpis">${a.kpis.map((k, i) => `
        <div class="kpi" style="--kc:${SPARK_COLORS[i % 4]}">
            <div class="kpi-top"><div><b>${(fmt[k.key] || String)(k.value)}</b><span class="mu">${esc(k.label)}</span></div>${delta(k.changePct)}</div>
            <div class="spark"><canvas id="spark-${i}"></canvas></div>
            <small class="mu">Last 30 days vs previous 30</small>
        </div>`).join("")}</div>
    <div class="dgrid">
        ${box("ch-orders", "Orders Overview", `<span class="mu" id="orders-sub"></span>`).replace('class="panel chart-panel"', 'class="panel chart-panel span2"')}
        ${box("ch-revenue", "Revenue", `<div class="seg" id="period-seg">
            <button data-p="monthly">Month</button><button data-p="weekly" class="on">Week</button><button data-p="daily">Day</button></div>`)}
        ${box("ch-status", "Order Status")}
        ${box("ch-category", "Revenue by Category")}
        ${box("ch-top", "Top Products by Revenue")}
        ${box("ch-rating", "Review Ratings")}
    </div>`;
}

function drawCharts(a) {
    chartInstances.splice(0).forEach(c => c.destroy());
    if (typeof Chart === "undefined") {
        toast("Chart library failed to load (check internet connection)", true);
        return;
    }
    Chart.defaults.font.family = "system-ui, Segoe UI, Roboto, sans-serif";
    Chart.defaults.color = "#6b7280";
    const mk = (id, cfg) => { const el = $("#" + id); if (!el) return null; const c = new Chart(el, cfg); chartInstances.push(c); return c; };
    const noData = (id, vals) => {
        if (vals.some(v => v > 0)) return false;
        $("#w-" + id).innerHTML = `<div class="empty">No data yet</div>`; return true;
    };
    const grid = { color: "#eef0f4" };

    // KPI sparklines
    a.kpis.forEach((k, i) => {
        const el = $("#spark-" + i); if (!el) return;
        const col = SPARK_COLORS[i % 4], g = el.getContext("2d").createLinearGradient(0, 0, 0, 64);
        g.addColorStop(0, col + "66"); g.addColorStop(1, col + "08");
        mk("spark-" + i, {
            type: "line",
            data: { labels: k.spark.map((_, j) => j), datasets: [{ data: k.spark, borderColor: col, backgroundColor: g, fill: true, tension: .4, borderWidth: 2, pointRadius: 0 }] },
            options: { maintainAspectRatio: false, events: [], plugins: { legend: { display: false }, tooltip: { enabled: false } }, scales: { x: { display: false }, y: { display: false, beginAtZero: true } } }
        });
    });

    // Revenue (line) + Orders (bars) share the Month / Week / Day toggle
    const periods = { daily: a.daily, weekly: a.weekly, monthly: a.monthly };
    const names = { daily: "last 30 days", weekly: "last 12 weeks", monthly: "last 12 months" };
    let cur = "weekly";
    const revEl = $("#ch-revenue"), rg = revEl.getContext("2d").createLinearGradient(0, 0, 0, 260);
    rg.addColorStop(0, "#4f46e540"); rg.addColorStop(1, "#4f46e505");
    const revChart = mk("ch-revenue", {
        type: "line",
        data: { labels: [], datasets: [{ label: "Revenue", data: [], borderColor: "#4f46e5", backgroundColor: rg, fill: true, tension: .35, borderWidth: 2.5, pointRadius: 3, pointHoverRadius: 5 }] },
        options: { maintainAspectRatio: false, interaction: { mode: "index", intersect: false },
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => " Revenue: " + money(c.parsed.y) } } },
            scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid, ticks: { callback: rupeeShort } } } }
    });
    const ordChart = mk("ch-orders", {
        type: "bar",
        data: { labels: [], datasets: [{ label: "Orders", data: [], backgroundColor: "#4f46e5", borderRadius: 4, maxBarThickness: 28 }] },
        options: { maintainAspectRatio: false,
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => " Orders: " + c.parsed.y } } },
            scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid, ticks: { precision: 0 } } } }
    });
    const setPeriod = p => {
        cur = p; const s = periods[p];
        revChart.data.labels = s.labels; revChart.data.datasets[0].data = s.revenue; revChart.update();
        ordChart.data.labels = s.labels; ordChart.data.datasets[0].data = s.orders; ordChart.update();
        $("#orders-sub").textContent = names[p];
        document.querySelectorAll("#period-seg button").forEach(b => b.classList.toggle("on", b.dataset.p === p));
    };
    document.querySelectorAll("#period-seg button").forEach(b => b.onclick = () => setPeriod(b.dataset.p));
    setPeriod(cur);

    // Order status doughnut
    const stKeys = Object.keys(a.orderStatus), stVals = stKeys.map(k => a.orderStatus[k]);
    if (!noData("ch-status", stVals)) mk("ch-status", {
        type: "doughnut",
        data: { labels: stKeys, datasets: [{ data: stVals, backgroundColor: stKeys.map((k, i) => STATUS_COLORS[k] || PALETTE[(i + 4) % PALETTE.length]), borderWidth: 2, borderColor: "#fff" }] },
        options: { maintainAspectRatio: false, cutout: "62%", plugins: { legend: { position: "bottom", labels: { usePointStyle: true, boxWidth: 8 } } } }
    });

    // Category doughnut
    const cKeys = Object.keys(a.categoryRevenue), cVals = cKeys.map(k => Number(a.categoryRevenue[k]));
    if (!noData("ch-category", cVals)) mk("ch-category", {
        type: "doughnut",
        data: { labels: cKeys, datasets: [{ data: cVals, backgroundColor: cKeys.map((_, i) => PALETTE[i % PALETTE.length]), borderWidth: 2, borderColor: "#fff" }] },
        options: { maintainAspectRatio: false, cutout: "62%", plugins: { legend: { position: "bottom", labels: { usePointStyle: true, boxWidth: 8 } }, tooltip: { callbacks: { label: c => ` ${c.label}: ${money(c.parsed)}` } } } }
    });

    // Top products (horizontal bars)
    const tp = a.topProducts, tpVals = tp.map(p => Number(p.revenue));
    if (!noData("ch-top", tpVals)) mk("ch-top", {
        type: "bar",
        data: { labels: tp.map(p => p.name.length > 22 ? p.name.slice(0, 21) + "…" : p.name), datasets: [{ data: tpVals, backgroundColor: PALETTE, borderRadius: 4, maxBarThickness: 22 }] },
        options: { indexAxis: "y", maintainAspectRatio: false,
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => ` ${money(c.parsed.x)} · ${tp[c.dataIndex].units} units` } } },
            scales: { x: { beginAtZero: true, grid, ticks: { callback: rupeeShort } }, y: { grid: { display: false } } } }
    });

    // Rating distribution
    const rKeys = Object.keys(a.ratingDistribution), rVals = rKeys.map(k => a.ratingDistribution[k]);
    if (!noData("ch-rating", rVals)) mk("ch-rating", {
        type: "bar",
        data: { labels: rKeys.map(k => k + " ★"), datasets: [{ data: rVals, backgroundColor: ["#e03131", "#f76707", "#f59f00", "#74b816", "#2f9e44"], borderRadius: 4, maxBarThickness: 40 }] },
        options: { maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => ` ${c.parsed.y} reviews` } } },
            scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid, ticks: { precision: 0 } } } }
    });
}

/* ===== start ===== */
window.addEventListener("hashchange", route);
renderNav(); if (user && user.role !== "SUPPLIER") cartItems().catch(() => {}); if (user && user.role === "SUPPLIER" && (!location.hash || location.hash === "#/" || location.hash === "#/manage")) location.hash = "#/supplier-dashboard";
route();
