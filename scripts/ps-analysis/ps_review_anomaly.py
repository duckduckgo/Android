import sys
import argparse
from datetime import datetime, timedelta, timezone
from collections import Counter
from statistics import mean, stdev
from google_play_scraper import reviews, Sort

def fetch_reviews_all_languages_countries(package_name, langs, countries, count=10000, date_range=None):
    """
    Fetch the latest reviews for all specified languages and countries, with an optional date range filter.

    Parameters:
        package_name (str): The package name to fetch reviews for.
        langs (list): A list of languages to fetch reviews in.
        countries (list): A list of countries to fetch reviews from.
        count (int): Maximum number of reviews to fetch per request. Default is 10000.
        date_range (tuple): A tuple containing two dates (start_date, end_date) as strings in 'YYYY-MM-DD' format.
                            Use None for open-ended ranges (e.g., ('2025-01-01', None) for "from this date onwards").
    
    Returns:
        list: A list of deduplicated and date-range-filtered reviews.
    """
    all_reviews = []
    review_ids = set()  # To track and deduplicate reviews
    dup_count = 0

    # Parse and prepare date range filters
    start_date = datetime.strptime(date_range[0], '%Y-%m-%d').date() if date_range and date_range[0] else None
    end_date = datetime.strptime(date_range[1], '%Y-%m-%d').date() if date_range and date_range[1] else None

    for lang in langs:
        for country in countries:
            try:
                reviews_data, _ = reviews(
                    package_name,
                    count=count,
                    lang=lang,
                    country=country,
                    sort=Sort.NEWEST
                )
                for review in reviews_data:
                    review_date = review.get('at').date()  # Assuming 'at' contains the review timestamp as a datetime
                    
                    # Apply date range filtering
                    if review_date:
                        if start_date and review_date < start_date:
                            continue
                        if end_date and review_date > end_date:
                            continue
                    
                    # Deduplicate reviews
                    if review['reviewId'] not in review_ids:
                        all_reviews.append(review)
                        review_ids.add(review['reviewId'])
                    else:
                        dup_count += 1
                print(f"Fetched {len(reviews_data)} reviews for lang={lang}, country={country}.")
            except Exception as e:
                print(f"Failed to fetch reviews for lang={lang}, country={country}: {e}")

    print(f"Total deduplicated reviews fetched: {len(all_reviews)}")
    print(f"Total duplicates removed: {dup_count}")
    return all_reviews


def analyze_anomalies(reviews_data):
    """
    Analyze reviews for anomalies in all star ratings on the most recent date.
    """
    date_reviews = {}
    for review in reviews_data:
        review_date = review['at'].date()
        if review_date not in date_reviews:
            date_reviews[review_date] = []
        date_reviews[review_date].append(review)

    all_dates = sorted(date_reviews.keys())
    num_days = (all_dates[-1] - all_dates[0]).days + 1 if all_dates else 0
    skew_warning = f"Warning: The reviews cover only {num_days} day(s), indicating potential skew." if num_days <= 3 else None

    daily_stats = []
    for date, reviews_on_date in date_reviews.items():
        star_counts = Counter([review['score'] for review in reviews_on_date])
        daily_stats.append({
            'date': date,
            'total_reviews': len(reviews_on_date),
            **{f"{star}_star": star_counts.get(star, 0) for star in range(1, 6)}
        })

    star_stats = {}
    for star in range(1, 6):
        star_counts = [day[f"{star}_star"] for day in daily_stats]
        star_avg = mean(star_counts) if star_counts else 0
        star_std = stdev(star_counts) if len(star_counts) > 1 else 0
        star_stats[star] = {'avg': star_avg, 'std': star_std}

    most_recent_date = max(date_reviews.keys()) if date_reviews else None
    recent_reviews = date_reviews[most_recent_date] if most_recent_date else []

    star_counts = Counter([review['score'] for review in recent_reviews])
    total_reviews = len(recent_reviews)

    anomalies = {}
    star_z_scores = {}
    if total_reviews > 0:
        for star in range(1, 6):
            count = star_counts.get(star, 0)
            star_stats[star]['count'] = count
            avg = star_stats[star]['avg']
            std = star_stats[star]['std']

            z_score = (count - avg) / std if std > 0 else 0
            star_z_scores[star] = z_score

            dynamic_threshold = 2 if std > 1 else 1.5
            if abs(z_score) > dynamic_threshold: # and z_score > 0:
                anomalies[star] = {
                    'count': count,
                    'z_score': z_score,
                    'avg': avg,
                    'std': std,
                    'threshold': dynamic_threshold
                }

    no_anomaly_reasons = []
    if total_reviews == 0:
        no_anomaly_reasons.append("No reviews available for the most recent date.")
    elif not anomalies:
        no_anomaly_reasons.append("No significant deviations (Z-scores within dynamic thresholds).")

    return {
        'most_recent_date': most_recent_date,
        'total_reviews': total_reviews,
        'anomalies': anomalies,
        'reviews_on_date': recent_reviews,
        'no_anomaly_reasons': no_anomaly_reasons,
        'num_days': num_days,
        'skew_warning': skew_warning,
        'star_stats': star_stats,
        'star_z_scores': star_z_scores
    }

def main():
    parser = argparse.ArgumentParser(description="Analyze Google Play Store reviews for anomalies.")
    parser.add_argument("--package_name", type=str, required=True, help="The package name of the app.")
    parser.add_argument("--langs", type=str, default="en", help="Comma-separated list of languages.")
    parser.add_argument("--countries", type=str, default="us", help="Comma-separated list of countries.")
    parser.add_argument("--count", type=int, default=10000, help="Number of reviews to fetch per language/country pair.")

    args = parser.parse_args()

    package_name = args.package_name
    langs = args.langs.split(",")
    countries = args.countries.split(",")
    count = args.count

    # Get yesterday's date in UTC
    yesterday = datetime.now(timezone.utc) - timedelta(days=2)
    reviews_data = fetch_reviews_all_languages_countries(package_name, langs, countries, count, (None, yesterday.strftime('%Y-%m-%d')))
    analysis_results = analyze_anomalies(reviews_data)

    print(f"\n--- Analysis Results ---")
    print(f"Most Recent Date: {analysis_results['most_recent_date']}")
    print(f"Total Reviews on Most Recent Date: {analysis_results['total_reviews']}")
    print(f"Number of Days Covered: {analysis_results['num_days']}")
    if analysis_results['skew_warning']:
        print(analysis_results['skew_warning'])

    print("\n--- Star Ratings Summary (⚠️ means anomaly) ---")
    for star, stats in analysis_results['star_stats'].items():
        avg = stats['avg']
        std = stats['std']
        count = stats['count']
        z_score = analysis_results['star_z_scores'].get(star, 0)
        anomaly = analysis_results['anomalies'].get(star)
        anomaly_flag = "⚠️" if anomaly else ""
        threshold_str = f"(Threshold: {anomaly['threshold']:.2f})" if anomaly else ""
        print(f"Average {star}-Star Reviews: {avg:.2f}, STD: {std:.2f}, Z-Score: {z_score:.2f} (Count: {count}) {anomaly_flag} {threshold_str}")

    print("\n--- Conditional Review Display ---")
    for star, stats in analysis_results['star_stats'].items():
        z_score = analysis_results['star_z_scores'].get(star, 0)
        anomaly = analysis_results['anomalies'].get(star)
        if anomaly:
            threshold = anomaly['threshold']
            if z_score > 0 and abs(z_score) > threshold:
                print(f"\nSignificant Positive Anomaly Detected for {star}-Star Reviews:")
                for review in analysis_results['reviews_on_date']:
                    if review['score'] == star:
                        print(f"Date: {review['at']}, Score: {review['score']}, Review: {review['content']}")
            elif z_score < 0 and abs(z_score) > threshold:
                print(f"\nWarning: Significant Negative Anomaly Detected for {star}-Star Reviews.")
                print(f"Reviews are NOT printed because Z-score ({z_score:.2f}) is negative.")


    print("\n--- Z-Score Guide (Dynamic Threshold-Aware) ---")
    print("- Z-Score: 0")
    print("  - Deviation Level: None")
    print("  - Interpretation: No deviation\n")
    print("- Z-Score: 0 to 'th'")
    print("  - Deviation Level: Minimal")
    print("  - Interpretation: Within expected variation\n")
    print("- Z-Score: 'th' to 2")
    print("  - Deviation Level: Moderate")
    print("  - Interpretation: Potential trend or anomaly\n")
    print("- Z-Score: >2")
    print("  - Deviation Level: Severe")
    print("  - Interpretation: Unusual, likely an anomaly\n")
    print("Note: 'th' represents the dynamic threshold:")
    print("  - 'th = 2' if STD > 1")
    print("  - 'th = 1.5' otherwise")

if __name__ == "__main__":
    main()
