# Text Doorman

An Android app that silences spam texts (especially the political fundraising texts that arrive from a new number every day) while you keep using your normal texting app.

## What it does

- Watches notifications from your texting app (Google Messages, Samsung Messages, or your default SMS app).
- For texts from numbers that **aren't** saved contacts, it scores the message with a small spam model that runs **on the phone**. If it looks like spam, the notification is dismissed. The text itself stays in your texting app.
- You can mark texts **Not spam** or **Spam**, and it learns from that on the device.
- Verification codes are let through, and picture-only texts aren't checked.
- Everything stays on the phone. There are no servers, accounts, ads, analytics, or network permission. See the [privacy policy](docs/privacy-policy.md).

Text Doorman is a spam filter, **not** a security product. It doesn't reliably catch phishing or scams.

### Nonpartisan

Campaign texts are scored on how they're written, not which party sent them. `tools/eval_fairness.py` checks this by swapping party references in each political text (Democrats ↔ Republicans, ActBlue ↔ WinRed, …) and comparing scores. The shipped model is trained with party-mirrored copies so both versions score the same (latest run: 97.3% caught as written vs 96.8% mirrored).

## How it works

| Piece | File |
|---|---|
| Notification listener: picks out texts from unknown senders and dismisses spam | `android/.../ScreenerService.kt` |
| Decision order: allowed/blocked senders → verification-code rule → model | `android/.../Screener.kt` |
| TF-IDF + logistic regression model, a Kotlin port of scikit-learn verified to match it | `android/.../SpamModel.kt` |
| On-device learning from corrections (a hashed n-gram layer over the frozen model) | `android/.../Personalizer.kt` |
| Screening history, corrections, feedback report | `ScreenLog.kt`, `FeedbackStore.kt`, `FeedbackReport.kt` |

No ML runtime is needed. The model is a 1.4 MB JSON file of vocabulary and weights, evaluated in plain Kotlin.

## Building

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.2.

```bash
cd android
./gradlew testDebugUnitTest   # unit tests, including Kotlin vs scikit-learn parity
./gradlew assembleRelease     # release APK with the shipped model
```

Build variants:
- **release** ships `models/generic_model.json` (copied to `app/src/release/assets/`), which is trained only on public and synthetic data.
- **debug** loads `app/src/debug/assets/screener_model.json`. That file isn't in the repo; generate one with `tools/export_model.py` (see below), or copy the generic model there.

## Rebuilding the model

Python 3.11+ with `scikit-learn` and `openpyxl`.

1. Download the public datasets into `data/public/` (each in its own folder):
   - `uci/` — [SMS Spam Collection](https://archive.ics.uci.edu/dataset/228/sms+spam+collection)
   - `mishra/` — [SMS Phishing Dataset](https://data.mendeley.com/datasets/f45bkkt8pr) (`Dataset_5971.csv`)
   - `jpm/` — [JPM corpus of 2024 campaign texts](https://figshare.com/articles/dataset/JPM-CORPUS-we-ve-asked-six-times_xlsx/28688072) (`jpm.xlsx`)
   - `spamhunter/` *(optional, evaluation only, see below)* — [SpamHunter](https://github.com/opmusic/SpamHunter_dataset) `sms_spam_texts`
2. Then run:

```bash
python tools/load_public.py          # -> data/public/combined.jsonl
python tools/gen_synthetic.py 3000   # -> data/synthetic_keep.jsonl (legitimate business texts, generated)
python tools/export_model.py --generic   # -> models/generic_model.json + test parity cases
```

Other tools (`export_unknown_sms.py`, `prelabel.py`, `eval_*.py`) work with a labeled dataset of your own texts in `data/dataset.jsonl`, exported from your phone over adb. That data is personal and is never committed.

## Credits

The shipped spam model was trained in part on these datasets, used under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) and modified (deduplicated, relabeled to block/keep, combined with other data). Inclusion doesn't imply endorsement.

- T. A. Almeida and J. M. Gómez Hidalgo, **SMS Spam Collection**, UCI Machine Learning Repository, 2011. <https://doi.org/10.24432/C5CC84>
- S. Mishra and D. Soni, **SMS Phishing Dataset for Machine Learning and Pattern Recognition**, Mendeley Data, V1, 2022. <https://doi.org/10.17632/f45bkkt8pr.1>
- **JPM corpus**, 580 campaign text messages from the 2024 US election cycle, Figshare, 2025.

SpamHunter (Tang et al., *Clues in Tweets*, ACM CCS 2022) has no stated license, so it's used **only to evaluate** the model, never to train it.

## License

[MIT](LICENSE). The dataset credits above must be kept with any distribution of the model.
