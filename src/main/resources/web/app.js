// SwagTournaments Web Editor — Vanilla JS SPA
// Auth is handled entirely by SwagAPI's shared session cookie — by the time this page
// loads, SwagAPI has already authenticated the visitor (or auth is disabled). See
// /swagapi/auth/status and /login?redirect=... for the shared auth contract.

// Current directory this page was served from (e.g. "/swagapi/swagtournaments/").
// Relative "api/..." paths below resolve against this so the editor works regardless
// of what prefix SwagAPI's IWebService mounts it under.
var API_BASE = window.location.pathname.replace(/[^/]*$/, '');
let livePoller = null;

// ── Identity ──────────────────────────────────────────────────────────────────

function loadWhoAmI() {
    fetch('/swagapi/auth/status', { credentials: 'include' })
        .then(r => r.json())
        .then(d => {
            const el = document.getElementById('whoami');
            if (el) el.textContent = d.username ? ('Signed in as ' + d.username) : '';
        })
        .catch(() => {});
}

// ── API helpers ───────────────────────────────────────────────────────────────

async function api(method, path, body) {
    const opts = {
        method,
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
    };
    if (body !== undefined) opts.body = JSON.stringify(body);
    try {
        const res = await fetch(API_BASE + path.replace(/^\//, ''), opts);
        if (res.status === 401 || res.status === 302) {
            stopLivePoller();
            window.location = '/login?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
            return null;
        }
        if (res.status === 204) return {};
        return await res.json();
    } catch (e) {
        console.error('API error', method, path, e);
        return null;
    }
}

const apiGet  = (path)        => api('GET',    path);
const apiPost = (path, body)  => api('POST',   path, body);
const apiPut  = (path, body)  => api('PUT',    path, body);
const apiDel  = (path)        => api('DELETE', path);

// ── Tab navigation ────────────────────────────────────────────────────────────

function switchTab(id) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === id));
    document.querySelectorAll('section').forEach(s => s.classList.toggle('active', s.id === 'tab-' + id));
    stopLivePoller();
    switch (id) {
        case 'dashboard':  loadDashboard(); break;
        case 'templates':  loadTemplates(); break;
        case 'schedule':   loadSchedule();  break;
        case 'history':    loadHistory();   break;
        case 'config':     loadConfig();    break;
    }
}

// ── Dashboard ─────────────────────────────────────────────────────────────────

async function loadDashboard() {
    await refreshLive();
    startLivePoller();
}

function startLivePoller() {
    stopLivePoller();
    livePoller = setInterval(refreshLive, 5000);
}

function stopLivePoller() {
    if (livePoller) { clearInterval(livePoller); livePoller = null; }
}

async function refreshLive() {
    const data = await apiGet('/api/live');
    if (!data) return;

    const el = document.getElementById('dashboard-content');
    if (!data.active) {
        el.innerHTML = `
            <div class="card">
                <p class="no-data">No active tournament.</p>
                <div class="mt-4" style="display:flex;gap:8px;">
                    <button class="btn btn-primary btn-sm" onclick="openStartModal()">Start Tournament</button>
                </div>
            </div>`;
        return;
    }

    const rows = (data.leaderboard || []).map((e, i) => `
        <tr>
            <td class="${i < 3 ? 'rank-' + (i+1) : ''}">#${e.rank}</td>
            <td>${esc(e.playerName)}</td>
            <td class="text-right">${fmtScore(e.score)}</td>
        </tr>`).join('');

    el.innerHTML = `
        <div class="card">
            <div class="flex-gap" style="margin-bottom:16px;">
                <span class="badge badge-active">LIVE</span>
                <strong>${esc(data.displayName)}</strong>
                <span class="text-muted" style="font-size:.8rem;">${esc(data.type)}</span>
            </div>
            <div class="live-box" style="margin-bottom:20px;">
                <div class="live-stat">
                    <div class="live-countdown" id="live-countdown">${fmtTime(data.timeRemainingSeconds)}</div>
                    <div class="ls-label">Remaining</div>
                </div>
                <div class="live-stat">
                    <div class="ls-val">${data.participantCount}</div>
                    <div class="ls-label">Participants</div>
                </div>
            </div>
            <table>
                <thead><tr><th>Rank</th><th>Player</th><th class="text-right">Score</th></tr></thead>
                <tbody>${rows || '<tr><td colspan="3" class="no-data">No participants yet.</td></tr>'}</tbody>
            </table>
            <div class="mt-4 flex-gap">
                <button class="btn btn-danger btn-sm" onclick="stopTournament()">Stop Tournament</button>
            </div>
        </div>`;

    let remaining = data.timeRemainingSeconds;
    const countdown = document.getElementById('live-countdown');
    if (countdown && remaining > 0) {
        const tick = setInterval(() => {
            remaining--;
            if (countdown && remaining >= 0) countdown.textContent = fmtTime(remaining);
            if (remaining <= 0) clearInterval(tick);
        }, 1000);
    }
}

async function stopTournament() {
    if (!confirm('Stop the active tournament?')) return;
    await apiPost('/api/live/stop');
    await refreshLive();
}

function openStartModal() {
    loadTemplateOptions().then(() => {
        document.getElementById('start-modal').classList.remove('hidden');
    });
}

async function loadTemplateOptions() {
    const data = await apiGet('/api/templates');
    if (!data) return;
    const sel = document.getElementById('start-template-id');
    sel.innerHTML = data.map(t => `<option value="${esc(t.id)}">${esc(t.displayName)} (${esc(t.id)})</option>`).join('');
}

async function confirmStartTournament() {
    const templateId = document.getElementById('start-template-id').value;
    const durationMinutes = parseInt(document.getElementById('start-duration').value, 10) || 30;
    if (!templateId) return;
    await apiPost('/api/live/start', { templateId, durationMinutes });
    closeModal('start-modal');
    await refreshLive();
}

// ── Templates ─────────────────────────────────────────────────────────────────

async function loadTemplates() {
    const data = await apiGet('/api/templates');
    if (!data) return;
    const grid = document.getElementById('templates-grid');
    if (!data.length) { grid.innerHTML = '<p class="no-data">No templates found.</p>'; return; }
    grid.innerHTML = data.map(t => `
        <div class="template-card" onclick="openTemplateEditor('${esc(t.id)}')">
            <div class="flex-gap" style="margin-bottom:6px;">
                <span class="badge ${t.status === 'ACTIVE' ? 'badge-active' : 'badge-idle'}">${t.status}</span>
            </div>
            <div class="tc-name">${esc(t.displayName)}</div>
            <div class="tc-meta">${esc(t.id)} &bull; ${esc(t.type)} &bull; ${esc(t.scoringMode)}</div>
        </div>`).join('');
}

async function openTemplateEditor(id) {
    let tmpl = {};
    if (id) {
        const data = await apiGet('/api/templates/' + id);
        if (!data) return;
        tmpl = data;
    }
    populateTemplateForm(tmpl, !!id);
    document.getElementById('template-modal').classList.remove('hidden');
}

function populateTemplateForm(t, editing) {
    document.getElementById('tmpl-editing-id').value = editing ? t.id : '';
    document.getElementById('tmpl-modal-title').textContent = editing ? 'Edit Template' : 'New Template';
    document.getElementById('tmpl-id').value = t.id || '';
    document.getElementById('tmpl-id').disabled = editing;
    document.getElementById('tmpl-display-name').value = t['display-name'] || '';
    document.getElementById('tmpl-description').value = t.description || '';
    document.getElementById('tmpl-type').value = t.type || 'CUSTOM';
    document.getElementById('tmpl-scoring-mode').value = t['scoring-mode'] || 'SUM';
    document.getElementById('tmpl-score-formula').value = t['score-formula'] || '1';
    document.getElementById('tmpl-target-score').value = t['target-score'] || 0;
    document.getElementById('tmpl-icon-material').value = t['icon-material'] || 'PAPER';
    document.getElementById('tmpl-bar-color').value = t['bar-color'] || 'WHITE';
    document.getElementById('tmpl-in-rotation').checked = t.schedule && t.schedule['in-rotation'];
    document.getElementById('tmpl-rotation-weight').value = t.schedule ? (t.schedule['rotation-weight'] || 1) : 1;
    document.getElementById('tmpl-start-fireworks').checked = t.cosmetics && t.cosmetics['start-fireworks'];
    document.getElementById('tmpl-end-fireworks').checked = t.cosmetics && t.cosmetics['end-fireworks'];
    document.getElementById('tmpl-sound-start').value = t.cosmetics ? (t.cosmetics['sound-start'] || '') : '';
    document.getElementById('tmpl-sound-end').value = t.cosmetics ? (t.cosmetics['sound-end'] || '') : '';
    document.getElementById('tmpl-msg-start').value = t.messages ? (t.messages.start || '') : '';
    document.getElementById('tmpl-msg-end').value = t.messages ? (t.messages.end || '') : '';
    document.getElementById('tmpl-defer-fishing').checked = t.integration && t.integration['defer-to-swagfishing'];
    document.getElementById('tmpl-defer-farming').checked = t.integration && t.integration['defer-to-swagfarming'];

    const rw1 = (t.rewards && t.rewards['1']) || {};
    const rw2 = (t.rewards && t.rewards['2']) || {};
    const rw3 = (t.rewards && t.rewards['3']) || {};
    const rwp = (t.rewards && t.rewards['participation']) || {};
    document.getElementById('tmpl-reward-1-money').value = rw1.money || 0;
    document.getElementById('tmpl-reward-1-cmds').value = (rw1.commands || []).join('\n');
    document.getElementById('tmpl-reward-2-money').value = rw2.money || 0;
    document.getElementById('tmpl-reward-2-cmds').value = (rw2.commands || []).join('\n');
    document.getElementById('tmpl-reward-3-money').value = rw3.money || 0;
    document.getElementById('tmpl-reward-3-cmds').value = (rw3.commands || []).join('\n');
    document.getElementById('tmpl-reward-p-cmds').value = (rwp.commands || []).join('\n');
    document.getElementById('tmpl-reward-p-enabled').checked = !!rwp.enabled;
}

function buildTemplatePayload() {
    const cmdLines = id => document.getElementById(id).value.split('\n').map(s => s.trim()).filter(Boolean);
    return {
        'id': document.getElementById('tmpl-id').value.trim(),
        'display-name': document.getElementById('tmpl-display-name').value,
        'description': document.getElementById('tmpl-description').value,
        'icon-material': document.getElementById('tmpl-icon-material').value,
        'bar-color': document.getElementById('tmpl-bar-color').value,
        'bar-style': 'SOLID',
        'type': document.getElementById('tmpl-type').value,
        'scoring-mode': document.getElementById('tmpl-scoring-mode').value,
        'score-formula': document.getElementById('tmpl-score-formula').value,
        'target-score': parseFloat(document.getElementById('tmpl-target-score').value) || 0,
        'farming-actions': [], 'mining-materials': [], 'combat-entities': [],
        'conditions': { biomes: [], worlds: [], time: 'ANY', weather: 'ANY', 'min-y': -64, 'max-y': 320, 'required-permission': '' },
        'schedule': {
            'in-rotation': document.getElementById('tmpl-in-rotation').checked,
            'rotation-weight': parseInt(document.getElementById('tmpl-rotation-weight').value, 10) || 1,
            'slots': []
        },
        'messages': {
            'start': document.getElementById('tmpl-msg-start').value,
            'end': document.getElementById('tmpl-msg-end').value,
            'warning': ''
        },
        'rewards': {
            '1': { money: parseFloat(document.getElementById('tmpl-reward-1-money').value)||0, commands: cmdLines('tmpl-reward-1-cmds') },
            '2': { money: parseFloat(document.getElementById('tmpl-reward-2-money').value)||0, commands: cmdLines('tmpl-reward-2-cmds') },
            '3': { money: parseFloat(document.getElementById('tmpl-reward-3-money').value)||0, commands: cmdLines('tmpl-reward-3-cmds') },
            'participation': { enabled: document.getElementById('tmpl-reward-p-enabled').checked, commands: cmdLines('tmpl-reward-p-cmds') }
        },
        'integration': {
            'defer-to-swagfishing': document.getElementById('tmpl-defer-fishing').checked,
            'defer-to-swagfarming': document.getElementById('tmpl-defer-farming').checked,
            'discord-channel-key': 'tournaments'
        },
        'cosmetics': {
            'start-fireworks': document.getElementById('tmpl-start-fireworks').checked,
            'end-fireworks': document.getElementById('tmpl-end-fireworks').checked,
            'sound-start': document.getElementById('tmpl-sound-start').value,
            'sound-end': document.getElementById('tmpl-sound-end').value
        }
    };
}

async function saveTemplate() {
    const editingId = document.getElementById('tmpl-editing-id').value;
    const payload = buildTemplatePayload();
    if (!payload.id) { alert('Template ID is required'); return; }

    let res;
    if (editingId) {
        res = await apiPut('/api/templates/' + editingId, payload);
    } else {
        res = await apiPost('/api/templates', payload);
    }

    if (res && !res.error) {
        closeModal('template-modal');
        loadTemplates();
    } else {
        alert('Save failed: ' + (res ? res.error : 'unknown error'));
    }
}

async function deleteTemplate() {
    const editingId = document.getElementById('tmpl-editing-id').value;
    if (!editingId) return;
    if (!confirm('Delete template "' + editingId + '"? This cannot be undone.')) return;
    const res = await apiDel('/api/templates/' + editingId);
    if (res !== null) {
        closeModal('template-modal');
        loadTemplates();
    }
}

// ── Schedule ──────────────────────────────────────────────────────────────────

async function loadSchedule() {
    const data = await apiGet('/api/schedule');
    if (!data) return;

    const slotsEl = document.getElementById('schedule-slots');
    if (!data.slots || !data.slots.length) {
        slotsEl.innerHTML = '<tr><td colspan="5" class="no-data">No cron slots configured. Add them in template YAML files.</td></tr>';
    } else {
        slotsEl.innerHTML = data.slots.map(s => `
            <tr>
                <td>${esc(s.templateId)}</td>
                <td>${esc(s.templateName)}</td>
                <td>${esc(s.day)}</td>
                <td>${esc(s.time)} (${s.durationMinutes}m)</td>
                <td>${esc(s.nextFire)}</td>
            </tr>`).join('');
    }

    const as = data.autoSchedule || {};
    document.getElementById('auto-enabled').checked = !!as.enabled;
    document.getElementById('auto-interval').value = as.intervalMinutes || 180;
    document.getElementById('auto-duration').value = as.durationMinutes || 30;
    document.getElementById('auto-min-players').value = as.minPlayers || 2;
    document.getElementById('auto-warning').value = as.warningMinutes || 5;

    const nextEl = document.getElementById('schedule-next');
    if (as.nextScheduledStart) {
        nextEl.textContent = 'Next: ' + as.nextScheduledStart + ' — ' + (as.nextScheduledTemplate || '');
    } else {
        nextEl.textContent = '';
    }
}

async function saveSchedule() {
    const payload = {
        enabled: document.getElementById('auto-enabled').checked,
        intervalMinutes: parseInt(document.getElementById('auto-interval').value, 10),
        durationMinutes: parseInt(document.getElementById('auto-duration').value, 10),
        minPlayers: parseInt(document.getElementById('auto-min-players').value, 10),
        warningMinutes: parseInt(document.getElementById('auto-warning').value, 10)
    };
    const res = await apiPost('/api/schedule', payload);
    if (res && res.saved) {
        showToast('Schedule saved');
        loadSchedule();
    }
}

// ── History ───────────────────────────────────────────────────────────────────

let historyPage = 0;

async function loadHistory(page) {
    if (page !== undefined) historyPage = page;
    const data = await apiGet('/api/history?page=' + historyPage + '&size=20');
    if (!data) return;

    const tbody = document.getElementById('history-tbody');
    if (!data.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="no-data">No tournament history yet.</td></tr>';
        return;
    }

    tbody.innerHTML = data.map(row => {
        const date = row.started_at ? new Date(row.started_at).toLocaleString() : '—';
        const dur = (row.started_at && row.ended_at)
            ? Math.round((row.ended_at - row.started_at) / 60000) + 'm' : '—';
        return `
            <tr>
                <td>${esc(String(row.id))}</td>
                <td>${esc(row.template_id || '')}</td>
                <td>${esc(date)}</td>
                <td>${esc(dur)}</td>
                <td>${row.participant_count || 0}</td>
                <td>
                    <button class="expand-btn" onclick="loadParticipants(${row.id}, this)">Expand</button>
                </td>
            </tr>
            <tr id="participants-${row.id}" style="display:none;">
                <td colspan="6"><div id="participants-content-${row.id}" style="padding:8px;"></div></td>
            </tr>`;
    }).join('');

    document.getElementById('hist-prev').disabled = historyPage === 0;
    document.getElementById('hist-next').disabled = data.length < 20;
}

async function loadParticipants(instanceId, btn) {
    const row = document.getElementById('participants-' + instanceId);
    const content = document.getElementById('participants-content-' + instanceId);

    if (row.style.display !== 'none') {
        row.style.display = 'none';
        btn.textContent = 'Expand';
        return;
    }

    const data = await apiGet('/api/history/' + instanceId);
    if (!data) return;

    if (!data.length) {
        content.innerHTML = '<em class="text-muted">No participants recorded.</em>';
    } else {
        const rows = data.map((p, i) => `
            <tr>
                <td>${i + 1}</td>
                <td>${esc(p.player_name)}</td>
                <td>${fmtScore(p.score)}</td>
                <td>${p.reward_given ? 'Yes' : 'No'}</td>
            </tr>`).join('');
        content.innerHTML = `
            <table>
                <thead><tr><th>Rank</th><th>Player</th><th>Score</th><th>Rewarded</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>`;
    }

    row.style.display = '';
    btn.textContent = 'Collapse';
}

// ── Config ────────────────────────────────────────────────────────────────────

async function loadConfig() {
    const data = await apiGet('/api/config');
    if (!data) return;
    const container = document.getElementById('config-form');
    container.innerHTML = '';

    const keys = Object.keys(data).sort();
    for (const key of keys) {
        const val = data[key];
        const div = document.createElement('div');
        div.className = 'form-group';
        div.innerHTML = `<label>${esc(key)}</label>
            <input type="${typeof val === 'boolean' ? 'checkbox' : (typeof val === 'number' ? 'number' : 'text')}"
                   id="cfg-${esc(key)}" name="${esc(key)}"
                   ${typeof val === 'boolean' ? (val ? 'checked' : '') : `value="${esc(String(val))}"`}
                   data-type="${typeof val}">`;
        container.appendChild(div);
    }

    const intData = await apiGet('/api/integrations');
    if (intData) renderIntegrations(intData);
}

async function saveConfig() {
    const inputs = document.querySelectorAll('#config-form input');
    const payload = {};
    inputs.forEach(inp => {
        if (!inp.name) return;
        const t = inp.dataset.type;
        if (t === 'boolean') payload[inp.name] = inp.checked;
        else if (t === 'number') payload[inp.name] = parseFloat(inp.value);
        else payload[inp.name] = inp.value;
    });
    const res = await apiPost('/api/config', payload);
    if (res && res.saved) showToast('Config saved');
}

function renderIntegrations(data) {
    const el = document.getElementById('integrations-pills');
    if (!el) return;
    const pills = [
        ['SwagFishing', data.swagFishing],
        ['SwagFarming', data.swagFarming],
        ['DiscordUtils', data.discordUtils],
        ['Vault', data.vault],
        ['PlaceholderAPI', data.placeholderApi]
    ];
    el.innerHTML = pills.map(([name, on]) =>
        `<span class="integration-pill ${on ? 'on' : 'off'}">${name} ${on ? '✓' : '✗'}</span>`
    ).join('');
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function fmtTime(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

function fmtScore(score) {
    if (score === Math.floor(score)) return String(Math.floor(score));
    return score.toFixed(2);
}

function esc(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function closeModal(id) {
    document.getElementById(id).classList.add('hidden');
}

function showToast(msg) {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        toast.style.cssText = 'position:fixed;bottom:24px;right:24px;background:#24242f;border:1px solid #5fe05f;color:#e4e4ea;padding:10px 20px;border-radius:8px;font-size:.875rem;z-index:999;transition:opacity .3s;';
        document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.style.opacity = '1';
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { toast.style.opacity = '0'; }, 2500);
}

// ── Init ──────────────────────────────────────────────────────────────────────

function initApp() {
    switchTab('dashboard');
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    loadWhoAmI();
    initApp();
});
