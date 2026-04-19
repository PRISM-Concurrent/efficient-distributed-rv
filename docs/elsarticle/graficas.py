import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

plt.rcParams.update({
    'font.family': 'serif',
    'font.size': 9,
    'axes.linewidth': 0.5,
    'axes.spines.top': False,
    'axes.spines.right': False,
})

colors = {
    'CollectFAInc': '#7F77DD',
    'CollectRAW':   '#1D9E75',
    'AspectJ':      '#D85A30',
}

# ── Datos Table C, threads=4 ──────────────────────────────────────────
ops    = [100, 500, 1_000, 5_000, 10_000, 50_000, 100_000]
gai_t4 = [0,   9,   20,   101,   204,   1_031,   2_005]
raw_t4 = [2,   9,   21,   101,   204,   1_028,   2_002]
aj_t4  = [2,   7,   16,    79,   160,     869,   1_757]

# ── Datos Table C, ops=10k ────────────────────────────────────────────
threads = [2,   4,   8,   16,  32,  64]
gai_op  = [160, 204, 373, 476, 560, 608]
raw_op  = [159, 204, 379, 482, 563, 593]
aj_op   = [133, 160, 250, 378, 439, 507]

# ── Datos Table D ─────────────────────────────────────────────────────
thr_labels = ['2 threads', '4 threads']
gai_v = [100, 100]
raw_v = [100, 100]
aj_v  = [90,  40]

fig, axes = plt.subplots(1, 3, figsize=(7.0, 2.4))
fig.subplots_adjust(wspace=0.38)

# ── Figura 1: overhead vs ops (log scale) ────────────────────────────
ax = axes[0]
ax.plot(ops, gai_t4, color=colors['CollectFAInc'], lw=1.2,
        marker='o', ms=3, label='CollectFAInc')
ax.plot(ops, raw_t4, color=colors['CollectRAW'],   lw=1.2,
        marker='s', ms=3, ls='--', label='CollectRAW')
ax.plot(ops, aj_t4,  color=colors['AspectJ'],      lw=1.2,
        marker='^', ms=3, label='AspectJ')
ax.set_xscale('log')
ax.set_yscale('log')
ax.set_xlabel('Operations', fontsize=8)
ax.set_ylabel('Time (ms)', fontsize=8)
ax.set_title('(a) Overhead vs ops\n(threads = 4)', fontsize=8)
ax.tick_params(labelsize=7)
ax.yaxis.set_major_formatter(ticker.FuncFormatter(
    lambda v, _: f'{int(v/1000)}s' if v >= 1000 else f'{int(v)}ms'))

# ── Figura 2: overhead vs threads ────────────────────────────────────
ax = axes[1]
ax.plot(threads, gai_op, color=colors['CollectFAInc'], lw=1.2,
        marker='o', ms=3, label='CollectFAInc')
ax.plot(threads, raw_op, color=colors['CollectRAW'],   lw=1.2,
        marker='s', ms=3, ls='--', label='CollectRAW')
ax.plot(threads, aj_op,  color=colors['AspectJ'],      lw=1.2,
        marker='^', ms=3, label='AspectJ')
ax.set_xlabel('Threads', fontsize=8)
ax.set_ylabel('Time (ms)', fontsize=8)
ax.set_title('(b) Overhead vs threads\n(ops = 10 000)', fontsize=8)
ax.set_xticks(threads)
ax.tick_params(labelsize=7)

# ── Figura 3: verdict accuracy ────────────────────────────────────────
ax = axes[2]
x   = np.arange(len(thr_labels))
w   = 0.22
ax.bar(x - w,   gai_v, w, color=colors['CollectFAInc'], label='CollectFAInc')
ax.bar(x,       raw_v, w, color=colors['CollectRAW'],   label='CollectRAW')
ax.bar(x + w,   aj_v,  w, color=colors['AspectJ'],      label='AspectJ')
ax.set_ylim(0, 115)
ax.set_ylabel('% linearizable', fontsize=8)
ax.set_title('(c) Verdict accuracy\n(ops = 100)', fontsize=8)
ax.set_xticks(x)
ax.set_xticklabels(thr_labels, fontsize=7)
ax.tick_params(labelsize=7)
ax.axhline(100, color='gray', lw=0.5, ls=':')

# ── Leyenda compartida ────────────────────────────────────────────────
handles, labels = axes[0].get_legend_handles_labels()
fig.legend(handles, labels,
           loc='lower center', ncol=3,
           fontsize=7.5, frameon=False,
           bbox_to_anchor=(0.5, -0.08))

plt.tight_layout()
plt.savefig('fig_benchmark.pdf', bbox_inches='tight', dpi=300)
plt.savefig('fig_benchmark.eps', bbox_inches='tight', dpi=300)
print("Guardado: fig_benchmark.pdf y fig_benchmark.eps")