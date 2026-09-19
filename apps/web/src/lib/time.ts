const minute = 60_000;
const hour = 60 * minute;
const day = 24 * hour;

export function exactDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function relativeTime(value?: string | null, now = Date.now()): string {
  if (!value) return "now";
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) return "now";
  const elapsed = now - timestamp;
  if (elapsed < 0) {
    const futureDays = Math.ceil(-elapsed / day);
    return futureDays <= 1 ? "tomorrow" : `in ${futureDays}d`;
  }
  if (elapsed < minute) return "now";
  if (elapsed < hour) return `${Math.floor(elapsed / minute)} min ago`;
  if (elapsed < day) return `${Math.floor(elapsed / hour)}h ago`;
  const today = new Date(now);
  const then = new Date(timestamp);
  const calendarDays = Math.floor((Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) - Date.UTC(then.getFullYear(), then.getMonth(), then.getDate())) / day);
  if (calendarDays === 1) return "yesterday";
  if (calendarDays < 7) return `${calendarDays}d ago`;
  if (calendarDays < 14) return "last week";
  if (calendarDays < 60) return "last month";
  if (calendarDays >= 365) return "long time ago";
  return `${Math.floor(calendarDays / 30)}mo ago`;
}

const unit = (value: number, name: string) => `${value} ${name}${value === 1 ? "" : "s"}`;

function addMonthsClamped(date: Date, months: number): Date {
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth() + months;
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return new Date(Date.UTC(year, month, Math.min(date.getUTCDate(), lastDay)));
}

export function ageFromDate(value?: string | null, now = new Date()): string {
  if (!value) return "Age unknown";
  const [year, month, dayOfMonth] = value.split("-").map(Number);
  if (!year || !month || !dayOfMonth) return "Age unknown";
  const birth = new Date(Date.UTC(year, month - 1, dayOfMonth));
  const today = new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
  if (birth > today) return "Age unknown";

  let totalMonths = (today.getUTCFullYear() - birth.getUTCFullYear()) * 12 + today.getUTCMonth() - birth.getUTCMonth();
  let monthAnchor = addMonthsClamped(birth, totalMonths);
  if (monthAnchor > today) {
    totalMonths--;
    monthAnchor = addMonthsClamped(birth, totalMonths);
  }

  if (totalMonths >= 12) {
    const years = Math.floor(totalMonths / 12);
    const months = totalMonths % 12;
    return `${unit(years, "year")} and ${unit(months, "month")}`;
  }

  const days = Math.floor((today.getTime() - monthAnchor.getTime()) / day);
  return `${unit(totalMonths, "month")} and ${unit(days, "day")}`;
}
