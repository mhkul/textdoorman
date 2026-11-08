"""Generate synthetic LEGITIMATE (keep) business texts: codes, shipping, bank
alerts, appointments, carrier notices, etc. Written from scratch, not derived
from anyone's real messages, so the output is safe to train a published model.

Deliberately includes things old spam corpora use too ("Reply STOP",
"Msg&data rates may apply", short links) so the model learns those phrases
alone don't mean spam.

Usage: python -I tools/gen_synthetic.py [count] -> data/synthetic_keep.jsonl
"""
import json
import random
import string
import sys

R = random.Random(42)

def pick(xs): return R.choice(xs)
def digits(n): return "".join(R.choice(string.digits) for _ in range(n))
def alnum(n): return "".join(R.choice(string.ascii_uppercase + string.digits) for _ in range(n))
def money(lo=3, hi=900): return f"${R.uniform(lo, hi):,.2f}"
def last4(): return digits(4)
def code(): return pick([digits(6), digits(6), digits(4), digits(5), digits(8), f"{digits(3)}-{digits(3)}", alnum(6)])
def day(): return pick(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"])
def date(): return pick([f"{R.randint(1,12)}/{R.randint(1,28)}", f"{R.randint(1,12)}/{R.randint(1,28)}/{pick(['25','26','2026'])}",
                         f"{pick(['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'])} {R.randint(1,28)}"])
def time(): return f"{R.randint(1,12)}:{pick(['00','15','30','45','10','20','40','50'])} {pick(['AM','PM','am','pm'])}"
def link(dom):
    dom = dom.replace(' ', '').replace("'", '').replace('&', '')
    return pick([f"https://{dom}/{alnum(R.randint(4,8)).lower()}", f"{dom}/{alnum(6).lower()}", f"https://{dom}"])
def first(): return pick(["Alex", "Jordan", "Sam", "Maria", "David", "Priya", "Chris", "Taylor", "Jamie", "Linda", "Mike", "Ana", "Wei", "Fatima", "John", "Sarah"])

FOOTERS = ["", "", "", " Reply STOP to opt out.", " Reply STOP to cancel, HELP for help.", " Msg&data rates may apply.",
           " Msg & data rates may apply. Reply STOP to end.", " Txt STOP to unsubscribe.", " Reply HELP for help, STOP to opt out."]
def ft(): return pick(FOOTERS)

OTP_BRANDS = ["Google", "Microsoft", "Apple", "Amazon", "PayPal", "Venmo", "Chase", "Wells Fargo", "Bank of America",
              "Capital One", "Discover", "Coinbase", "Instagram", "Facebook", "WhatsApp", "TikTok", "Discord", "Steam",
              "Uber", "Lyft", "DoorDash", "Instacart", "Airbnb", "Netflix", "Spotify", "LinkedIn", "X", "Snapchat",
              "Robinhood", "Fidelity", "Schwab", "Zelle", "Cash App", "Etsy", "eBay", "Target", "Walmart", "Best Buy",
              "Dropbox", "Slack", "Zoom", "Reddit", "Yahoo", "Twilio", "Okta", "Duo", "Experian", "Credit Karma", "Intuit",
              "TurboTax", "H&R Block", "IRS", "SSA", "Login.gov", "ID.me", "Verizon", "T-Mobile", "AT&T", "Comcast", "Xfinity"]

def otp():
    b, c = pick(OTP_BRANDS), code()
    return pick([
        f"Your {b} verification code is {c}.",
        f"{c} is your {b} verification code.{pick(['', ' Do not share it.', ' Don’t share this code with anyone.'])}",
        f"{b}: Your code is {c}. Never share this code. {b} will never call you to ask for it.",
        f"Use {c} to verify your {b} account. It expires in {pick([5,10,15,30])} minutes.",
        f"<#> {c} is your {b} code. {alnum(11)}",
        f"{b} security code: {c}. If you didn't request this, you can ignore this message.{ft()}",
        f"Your one-time passcode for {b} is {c}. Valid for {pick([5,10,15])} min.",
        f"[{b}] Verification code: {c}",
        f"G-{digits(6)} is your Google verification code.",
        f"Your {b} login code: {c}. Don't share it with anyone, even {b} support.",
        f"{b}: {c} is your code to sign in. If this wasn't you, reset your password in the app.",
        f"Enter {c} to confirm your phone number with {b}.{ft()}",
    ])

SHOPS = ["Amazon", "Target", "Walmart", "Best Buy", "Etsy", "eBay", "Costco", "Home Depot", "Lowe's", "Wayfair",
         "Chewy", "Nike", "Apple", "Samsung", "Kohl's", "Macy's", "IKEA", "Sephora", "Ulta", "Old Navy", "REI"]
CARRIERS = ["UPS", "FedEx", "USPS", "DHL", "OnTrac", "Amazon Logistics", "LaserShip"]

def shipping():
    s, c = pick(SHOPS), pick(CARRIERS)
    o = pick([f"#{digits(3)}-{digits(7)}-{digits(7)}", f"#{digits(8)}", f"{alnum(10)}", f"#US{digits(8)}"])
    trk = pick(["1Z" + alnum(16), digits(12), "9400" + digits(18), digits(10)])
    return pick([
        f"{s}: Your order {o} has shipped! Track it: {link('tinyurl.com' if R.random()<.2 else s.lower().replace(' ','').replace(chr(39),'')+'.com')}{ft()}",
        f"Delivered: Your {s} package was left at the {pick(['front door','mailroom','back porch','garage','parcel locker'])}.",
        f"{c}: Your package {trk} is out for delivery today.{ft()}",
        f"{c} Delivery Notification: Scheduled delivery {day()} {date()} by {time()}. Track: {link(c.lower().replace(' ','')+'.com')}",
        f"Your {s} order {o} is out for delivery and will arrive by {time()}.",
        f"Delivered - {o} - {link('a.co/d')}",
        f"{s}: Good news! Your order is ready for pickup at your local store. Bring your order number {o}.",
        f"Arriving today: {pick(['1 item','2 items','3 items','your package'])} from {s}. Track at {link(s.lower().replace(' ','')+'.com')}",
        f"{c}: A delivery attempt was made {date()}. We'll try again next business day. Manage at {link(c.lower()+'.com')}",
        f"Your {s} return has been received. Your refund of {money(5,300)} will post in {pick([3,5,7])}-{pick([7,10])} business days.",
        f"{s}: Your order {o} was canceled as requested. No charge was made.",
    ])

BANKS = ["Chase", "Wells Fargo", "Bank of America", "Capital One", "Citi", "US Bank", "PNC", "Discover", "Amex",
         "Ally", "Navy Federal", "USAA", "TD Bank", "Truist", "SoFi", "Chime", "Golden 1", "Alliant"]

def bank():
    b = pick(BANKS)
    m = pick(["STARBUCKS", "SHELL OIL", "AMAZON MKTPL", "SAFEWAY", "TARGET", "NETFLIX.COM", "UBER *TRIP", "COSTCO WHSE",
              "CHIPOTLE", "SPOTIFY", "APPLE.COM/BILL", "HOME DEPOT", "WALGREENS", "DOORDASH", "OPENAI", "AIRBNB"])
    return pick([
        f"{b}: A charge of {money()} at {m} was made on your card ending in {last4()}. If you don't recognize it, call the number on the back of your card.",
        f"{b} Alert: Your balance in account ending {last4()} is {money(20,9000)} as of {date()}.",
        f"{b}: Direct deposit of {money(400,4500)} posted to your checking account ending {last4()}.",
        f"Fraud alert from {b}: Did you attempt a {money()} purchase at {m}? Reply YES or NO.{ft()}",
        f"{b}: Your payment of {money(25,1200)} to your credit card ending {last4()} has posted. Thank you.",
        f"{b} Alert: Your statement is ready. Minimum payment of {money(25,200)} is due {date()}.",
        f"{b}: You sent {money(5,500)} with Zelle to {first()}. Ref# {alnum(8)}",
        f"{b}: {first()} sent you {money(5,500)} with Zelle. It's in your account now.",
        f"{b} Alert: Low balance. Your available balance in account ending {last4()} is below {pick(['$100','$50','$25'])}.",
        f"FreeMsg: {b} card ending {last4()}: {date()} {money()} {m}. If authorized reply YES, if not reply NO.",
        f"{b}: Your new card ending {last4()} has shipped and should arrive in 5-7 business days.",
        f"{b}: We declined a {money()} transaction at {m} on card ending {last4()}. Text Y if it was you to try again.",
        f"{b} Alert: A withdrawal of {money(20,800)} was made from your account ending {last4()}.",
    ])

PAY = ["PayPal", "Venmo", "Cash App", "Apple Card", "Affirm", "Klarna", "Afterpay"]
def payments():
    p = pick(PAY); who = pick(["Netflix", "Spotify", "Hulu", "Microsoft", "Adobe", "Comcast", "PG&E", "Geico", "Progressive", first()])
    return pick([
        f"{p}: Your payment of {money(5,300)} to {who} was successful.",
        f"{p}: Your automatic payment to {who} for {money(5,200)} didn't go through. Update your payment method in the app.",
        f"{p}: You received {money(5,400)} from {first()}.",
        f"Reminder: Your {p} payment of {money(20,250)} is due {date()}. Pay in the app or at {link(p.lower().replace(' ','')+'.com')}",
        f"{p}: Thanks for your purchase of {money()} at {pick(SHOPS)}.",
        f"{who}: Your bill of {money(30,400)} is ready. AutoPay is scheduled for {date()}.",
        f"{who}: Payment received - thank you! Your account balance is now {pick(['$0.00', money(0,50)])}.",
    ])

CLINICS = ["Kaiser Permanente", "Providence", "Sutter Health", "Mayo Clinic", "MultiCare", "Valley Medical Center",
           "Swedish", "Northside Family Dental", "Bright Smiles Dental", "Pacific Eye Care", "Downtown Urgent Care",
           "Lakeside Pediatrics", "Summit Physical Therapy", "Riverside Dermatology"]
PHARM = ["CVS", "Walgreens", "Rite Aid", "Costco Pharmacy", "Walmart Pharmacy", "Safeway Pharmacy", "Fred Meyer Pharmacy"]

def health():
    c, ph = pick(CLINICS), pick(PHARM)
    return pick([
        f"{c}: Reminder of your appointment on {date()} at {time()}. Reply C to confirm or R to reschedule.",
        f"Appointment confirmed with {c} for {day()} {date()} at {time()}. Please arrive 15 minutes early.",
        f"{c}: Your test results are available. Log in to your patient portal to view them.",
        f"{ph}: Your prescription is ready for pickup. Rx# ending {last4()}.{ft()}",
        f"{ph}: Your refill for Rx ending {last4()} is being processed and will be ready {date()}.",
        f"{ph}: Your prescription was transferred successfully. We'll text you when it's ready.",
        f"{c}: You have a new message from your care team. Sign in to {link('mychart.com')} to read it.",
        f"Hi {first()}, this is {c} confirming your visit tomorrow at {time()}. Reply YES to confirm.",
        f"{c}: Your telehealth visit starts at {time()}. Join here: {link('zoom.us/j')}",
        f"Your health plan: Your new member ID card is on the way. You can view a digital copy in the app.",
        f"{c}: Please complete your pre-visit check-in before {date()}: {link('checkin.' + c.split()[0].lower() + '.org')}",
    ])

CARRIER = ["Verizon", "T-Mobile", "AT&T", "Mint Mobile", "Visible", "Cricket", "US Cellular", "Google Fi"]
def carrier():
    c = pick(CARRIER)
    return pick([
        f"{c} Msg: Your bill for {money(30,250)} is ready. AutoPay will process it on {date()}.",
        f"{c} Msg: You've used 75% of your data for this bill cycle.",
        f"{c}: Welcome to {pick(['Canada','Mexico','the UK','France','Japan'])}! Your plan includes international roaming. Standard rates apply.",
        f"{c} Msg: A new device was activated on your line. If you didn't do this, call {pick(['611','*611'])}.",
        f"{c}: Your payment of {money(30,250)} was received. Thank you!",
        f"{c} Msg: Your SIM change request was completed. If you didn't request this, call 611 from another phone immediately.",
        f"{c}: Thanks for reporting spam. We'll review the message you forwarded to 7726.",
        f"{c} Msg: Your order has shipped. Track at {link(c.lower().replace(' ','').replace('&','')+'.com/track')}",
        f"{c} Msg: Scheduled maintenance tonight from 1-4 AM may briefly affect service in your area.",
    ])

UTIL = ["PG&E", "Puget Sound Energy", "Seattle City Light", "Duke Energy", "ConEd", "Xfinity", "Spectrum", "Cox", "Waste Management", "City Water Dept"]
def utilities():
    u = pick(UTIL)
    return pick([
        f"{u}: We're aware of an outage in your area. Estimated restoration: {time()}.",
        f"{u}: Power has been restored at your address. Thank you for your patience.",
        f"{u}: Your bill of {money(30,300)} is due {date()}. Pay at {link(u.lower().replace(' ','').replace('&','')+'.com')}",
        f"{u}: Your technician is on the way and will arrive between {time()} and {time()}.",
        f"{u}: Reminder: Your service appointment is {day()} {date()} between {time()} and {time()}.",
        f"{u}: Your pickup has been rescheduled to {day()} due to the holiday.",
    ])

SCHOOLS = ["Lincoln High School", "Westside Elementary", "Lakeview College", "State University", "Central Community College", "Maple Middle School"]
def school():
    s = pick(SCHOOLS)
    return pick([
        f"{s} ALERT: Campus closed today due to weather. Check email for updates.",
        f"{s}: Classes are canceled {day()}. Visit {link(s.split()[0].lower()+'.edu/alerts')} for details.",
        f"{s}: Your student {first()} was marked absent today. Please call the attendance office.",
        f"{s}: Reminder - registration for {pick(['spring','fall','winter','summer'])} quarter opens {date()}.",
        f"{s} Emergency Alert: Police activity near campus. Avoid the {pick(['north','south','east','west'])} lot until further notice.",
        f"{s}: Your financial aid has been disbursed. Check your student account for details.",
        f"{s}: Transcript order {alnum(8)} has been sent.{ft()}",
    ])

TRAVEL = ["Delta", "United", "American Airlines", "Alaska Airlines", "Southwest", "JetBlue", "Hertz", "Enterprise", "Marriott", "Hilton", "Airbnb", "Expedia", "Booking.com"]
def travel():
    t = pick(TRAVEL)
    return pick([
        f"{t}: Flight {pick(['DL','UA','AA','AS','WN','B6'])}{R.randint(100,2999)} to {pick(['SEA','LAX','JFK','ORD','DEN','ATL','SFO','BOS'])} departs {time()} from gate {pick('ABCDN')}{R.randint(1,40)}.",
        f"{t}: Your flight is delayed. New departure time is {time()}.",
        f"{t}: Check-in is now open for your trip on {date()}. Confirmation {alnum(6)}.",
        f"{t}: Your reservation {alnum(8)} is confirmed for {date()}.",
        f"{t}: Your car is ready. Go to space {R.randint(1,300)} in lot {pick('ABCD')}.",
        f"{t}: Your host sent you a message. Open the app to reply.",
        f"{t}: Your booking for {date()} has been canceled and a refund of {money(40,800)} issued.",
    ])

RIDE = ["Uber", "Lyft", "DoorDash", "Uber Eats", "Instacart", "Grubhub", "Postmates"]
def gig():
    r = pick(RIDE)
    return pick([
        f"{r}: Your driver {first()} is arriving in {R.randint(1,9)} min in a {pick(['white','black','silver','blue','gray'])} {pick(['Toyota Camry','Honda Civic','Tesla Model 3','Ford Escape','Nissan Altima'])}.",
        f"{r}: Your order from {pick(['Chipotle','Panda Express','Thai Basil','Subway','Pizza Hut','Safeway'])} is on its way.",
        f"{r}: Your Dasher is approaching with your order.",
        f"{r}: {first()} is shopping for your order. You'll be notified about any replacements.",
        f"{r}: Your order was delivered. Enjoy!",
        f"Hi, it's {first()}, your {r} driver. I'm outside in front.",
        f"{r}: Your receipt for {money(8,90)} is in your email.",
    ])

PERSONALISH = [
    "Hi, this is {n} from {c}. Just confirming we're still on for {d} at {t}?",
    "Hey it's {n} from the {x} - your {y} is ready to pick up whenever.",
    "This is {n} with {c}, following up on your quote request. Call or text back anytime.",
    "{c}: Your vehicle is ready for pickup. Total due {m}.",
    "Hi! It's {n}, the dog walker. All set for {d} at {t}.",
    "This is {n}, your property manager. Maintenance will be in unit {u} {d} between {t} and {t2}.",
]
def local():
    return pick(PERSONALISH).format(
        n=first(), c=pick(["Main St Auto", "Joe's Plumbing", "Evergreen Realty", "Sunset Vet Clinic", "City Tire", "Bayside Cleaners"]),
        d=day(), t=time(), t2=time(), x=pick(["repair shop", "tailor", "bike shop", "framing store"]),
        y=pick(["order", "bike", "jacket", "phone"]), m=money(40, 900), u=f"{R.randint(1,40)}{pick('ABCD')}")

GENERATORS = [(otp, 22), (shipping, 15), (bank, 15), (payments, 8), (health, 10), (carrier, 7),
              (utilities, 5), (school, 5), (travel, 5), (gig, 5), (local, 3)]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    fns, weights = zip(*GENERATORS)
    seen, out = set(), []
    tries = 0
    while len(out) < n and tries < n * 20:
        tries += 1
        f = R.choices(fns, weights)[0]
        t = " ".join(f().split())
        if t not in seen:
            seen.add(t)
            out.append({"source": "synthetic", "category": f.__name__, "label": "keep", "text": t})
    with open("data/synthetic_keep.jsonl", "w", encoding="utf-8") as fh:
        for r in out:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    from collections import Counter
    print(len(out), dict(Counter(r["category"] for r in out)))


if __name__ == "__main__":
    main()
