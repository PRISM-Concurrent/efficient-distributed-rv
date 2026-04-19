import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ── Estilo general ────────────────────────────────────────────────────
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

# ── Datos (Se mantienen exactos a tu versión) ────────────────────────
ops     = [100, 500, 1_000, 5_000, 10_000, 50_000, 100_000]
gai_t4  = [0.1, 9, 20, 101, 204, 1031, 2005] 
raw_t4  = [2, 9, 21, 101, 204, 1028, 2002]
aj_t4   = [2, 7, 16, 79, 160, 869, 1757]

threads = [2, 4, 8, 16, 32, 64]
gai_op  = [160, 204, 373, 476, 560, 608]
raw_op  = [159, 204, 379, 482, 563, 593]
aj_op   = [133, 160, 250, 378, 439, 507]

thr_d   = [2, 4, 8, 16, 32, 64]
gai_d, raw_d = [100]*6, [100]*6
aj_d    = [45, 79, 98, 99, 100, 100]

gai_partial = [91, 85, 84, 97, 100, 100]
raw_partial = [95, 96, 92, 94, 99, 99]
aj_partial  = [36, 67, 98, 100, 100, 100]

# ── Layout: Más altura y más espacio entre subplots ──────────────────
fig, axes = plt.subplots(2, 2, figsize=(7.2, 6.5)) # Más alto para dar aire
fig.subplots_adjust(hspace=1.0, wspace=0.38, top=0.88, bottom=0.12)

# ══════════════════════════════════════════════════════════════════════
# (a) Overhead vs ops
# ══════════════════════════════════════════════════════════════════════
ax = axes[0, 0]
ax.plot(ops, gai_t4, color=colors['CollectFAInc'], lw=1.2, marker='o', ms=3, label='CollectFAInc')
ax.plot(ops, raw_t4, color=colors['CollectRAW'], lw=1.2, marker='s', ms=3, ls='--', label='CollectRAW')
ax.plot(ops, aj_t4,  color=colors['AspectJ'], lw=1.2, marker='^', ms=3, label='AspectJ')
ax.set_xscale('log')
ax.set_yscale('log')
ax.set_xlabel('Operations', fontsize=8)
ax.set_ylabel('Time (ms)', fontsize=8)
ax.set_title('(a) Instrumentation overhead vs. ops\n(threads = 4)', fontsize=8, pad=12)
ax.tick_params(labelsize=7)

# ══════════════════════════════════════════════════════════════════════
# (b) Overhead vs threads
# ══════════════════════════════════════════════════════════════════════
ax = axes[0, 1]
ax.plot(threads, gai_op, color=colors['CollectFAInc'], lw=1.2, marker='o', ms=3)
ax.plot(threads, raw_op, color=colors['CollectRAW'], lw=1.2, marker='s', ms=3, ls='--')
ax.plot(threads, aj_op,  color=colors['AspectJ'], lw=1.2, marker='^', ms=3)
ax.set_xlabel('Threads', fontsize=8)
ax.set_ylabel('Time (ms)', fontsize=8)
ax.set_title('(b) Instrumentation overhead vs. threads\n(ops = 10,000)', fontsize=8, pad=12)
ax.set_xticks(threads)
ax.tick_params(labelsize=7)

# ══════════════════════════════════════════════════════════════════════
# (c) Verdict accuracy — correct impl.
# ══════════════════════════════════════════════════════════════════════
ax = axes[1, 0]
x = np.arange(len(thr_d))
w = 0.25
ax.bar(x - w, gai_d, w, color=colors['CollectFAInc'], label='CollectFAInc')
ax.bar(x,     raw_d, w, color=colors['CollectRAW'],   label='CollectRAW')
ax.bar(x + w, aj_d,  w, color=colors['AspectJ'],      label='AspectJ')
ax.set_ylim(0, 130) # Más espacio arriba para la leyenda interna
ax.set_ylabel('% linearizable (correct verdicts)', fontsize=8)
ax.set_title('(c) Verdict accuracy — correct impl.\n(CLQ, ops = 100)', fontsize=8, pad=12)
ax.set_xticks(x)
ax.set_xticklabels([str(t) for t in thr_d])
ax.set_xlabel('Threads', fontsize=8)
ax.legend(fontsize=6.5, frameon=True, framealpha=0.8, loc='upper left') 
ax.axhline(100, color='gray', lw=0.5, ls=':')

# ══════════════════════════════════════════════════════════════════════
# (d) Violation detection
# ══════════════════════════════════════════════════════════════════════
ax = axes[1, 1]
# Detección de violaciones (100 - %True)
psq_gai = [100-v for v in gai_partial]
psq_raw = [100-v for v in raw_partial]
psq_aj  = [100-v for v in aj_partial]

x = np.arange(len(thr_d))
ax.bar(x - w, psq_gai, w, color=colors['CollectFAInc'], label='C-FAInc (PSQ)', hatch='//')
ax.bar(x,     psq_raw, w, color=colors['CollectRAW'],   label='C-RAW (PSQ)',   hatch='//')
ax.bar(x + w, psq_aj,  w, color=colors['AspectJ'],      label='AspectJ (PSQ)',  hatch='//')

ax.set_ylim(0, 130)
ax.set_ylabel('% violations detected', fontsize=8)
ax.set_title('(d) Violation detection\n(PSQ = PartialSyncQueue)', fontsize=8, pad=12)
ax.set_xticks(x)
ax.set_xticklabels([str(t) for t in thr_d])
ax.set_xlabel('Threads', fontsize=8)
ax.legend(fontsize=6, frameon=True, framealpha=0.8, loc='upper left')
ax.axhline(100, color='gray', lw=0.5, ls=':')

# ── Leyenda global superior ──────────────────────────────────────────
handles, labels = axes[0, 0].get_legend_handles_labels()
fig.legend(handles, labels, loc='upper center', ncol=3, 
           fontsize=8, frameon=False, bbox_to_anchor=(0.5, 0.97))

plt.savefig('fig_benchmark.pdf', bbox_inches='tight')
print("Figura optimizada generada.")