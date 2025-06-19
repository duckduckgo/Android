export function getDueOn(workingDays: number): string {
  if (workingDays < 0) {
    throw new Error('getDueOn is not supported for past dates')
  }

  let date = new Date()
  const weekends = Math.floor(workingDays / 5)
  const dueOnDay = date.getDay() + weekends * 2 + workingDays
  // additional days are all weekends + 2 if the due on day ends on Saturday
  const additionalDays = weekends * 2 + ((dueOnDay % 7) % 6 === 0 ? 2 : 0)

  date.setDate(date.getDate() + workingDays + additionalDays)
  const offset = date.getTimezoneOffset()
  date = new Date(date.getTime() - offset * 60 * 1000)
  return date.toISOString().split('T')[0]
}