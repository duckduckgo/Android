import sys
import argparse
from datetime import datetime, timedelta
from collections import Counter
from statistics import mean, stdev
from google_play_scraper import reviews, Sort

def fetch_reviews_all_languages_countries(package_name, langs, countries, count=10000):
    """
    Fetch the latest reviews for all specified languages and countries.

    Args:
        package_name (str): The app's package name.
        langs (list): List of languages to fetch reviews for.
        countries (list): List of countries to fetch reviews for.
        count (int): Number of reviews to fetch per language/country.

    Returns:
        list: Combined list of deduplicated reviews from all languages and countries.
    """
    all_reviews = []
    review_ids = set()  # To track and deduplicate reviews
    dup_count = 0

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

    Args:
        reviews_data (list): List of review data.

    Returns:
        dict: Analysis results including the most recent date, anomalies, and statistics.
    """
    # Group reviews by date
    date_reviews = {}
    for review in reviews_data:
        review_date = review['at'].date()
        if review_date not in date_reviews:
            date_reviews[review_date] = []
        date_reviews[review_date].append(review)

    # Calculate the number of days covered by the reviews
    all_dates = sorted(date_reviews.keys())
    num_days = (all_dates[-1] - all_dates[0]).days + 1 if all_dates else 0

    # Check for skew in review distribution
    skew_warning = None
    if num_days <= 3:
        skew_warning = f"Warning: The reviews cover only {num_days} day(s), indicating potential skew."

    # Analyze daily statistics for all star ratings
    daily_stats = []
    for date, reviews_on_date in date_reviews.items():
        star_counts = Counter([review['score'] for review in reviews_on_date])
        daily_stats.append({
            'date': date,
            'total_reviews': len(reviews_on_date),
            **{f"{star}_star": star_counts.get(star, 0) for star in range(1, 6)}
        })

    # Calculate averages and standard deviations for all star ratings
    star_stats = {}
    for star in range(1, 6):
        star_counts = [day[f"{star}_star"] for day in daily_stats]
        star_avg = mean(star_counts) if star_counts else 0
        star_std = stdev(star_counts) if len(star_counts) > 1 else 0
        star_stats[star] = {
            'avg': star_avg,
            'std': star_std
        }

    # Find the most recent date with reviews
    most_recent_date = max(date_reviews.keys()) if date_reviews else None
    recent_reviews = date_reviews[most_recent_date] if most_recent_date else []

    # Analyze anomalies for the most recent date using Z-scores
    star_counts = Counter([review['score'] for review in recent_reviews])
    total_reviews = len(recent_reviews)

    anomalies = {}
    if total_reviews > 0:
        for star in range(1, 6):
            count = star_counts.get(star, 0)
            avg = star_stats[star]['avg']
            std = star_stats[star]['std']

            if std > 0:
                z_score = (count - avg) / std
                # Adjust Z-score threshold dynamically based on variability
                dynamic_threshold = 2 if std > 1 else 1.5
                if abs(z_score) > dynamic_threshold:
                    anomalies[star] = {
                        'count': count,
                        'z_score': z_score,
                        'avg': avg,
                        'std': std,
                        'threshold': dynamic_threshold,
                        'star': star  # Add star rating to anomaly data for later use
                    }

    # Include reasons why no anomalies were detected
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
        'star_stats': star_stats
    }


def main():
    parser = argparse.ArgumentParser(description="Analyze Google Play Store reviews for anomalies.")
    parser.add_argument(
        "--package_name",
        type=str,
        required=True,
        help="The package name of the app (e.g., com.duckduckgo.mobile.android).",
    )
    parser.add_argument(
        "--langs",
        type=str,
        default="en",
        help="Comma-separated list of languages to fetch reviews for (default: 'en').",
    )
    parser.add_argument(
        "--countries",
        type=str,
        default="us",
        help="Comma-separated list of countries to fetch reviews for (default: 'us').",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=10000,
        help="Number of reviews to fetch per language/country pair (default: 10000).",
    )

    args = parser.parse_args()

    package_name = args.package_name
    langs = args.langs.split(",")
    countries = args.countries.split(",")
    count = args.count

    print("Fetching the latest reviews...")
    reviews_data = fetch_reviews_all_languages_countries(package_name, langs, countries, count=count)

    print("Analyzing anomalies...")
    analysis_results = analyze_anomalies(reviews_data)

    print(f"\n--- Analysis Results ---")
    print(f"Most Recent Date: {analysis_results['most_recent_date']}")
    print(f"Total Reviews on Most Recent Date: {analysis_results['total_reviews']}")
    print(f"Number of Days Covered: {analysis_results['num_days']}")
    if analysis_results['skew_warning']:
        print(f"{analysis_results['skew_warning']}")
    for star, stats in analysis_results['star_stats'].items():
        print(f"Average {star}-Star Reviews: {stats['avg']:.2f}, STD: {stats['std']:.2f}")

    if analysis_results['anomalies']:
        print("\nAnomalies Found:")
        for star, data in analysis_results['anomalies'].items():
            print(f"{star}-Star Reviews: {data['count']} (Z-Score: {data['z_score']:.2f}, Avg: {data['avg']:.2f}, STD: {data['std']:.2f}, Threshold: {data['threshold']})")

        # Print reviews related to anomalies
        print("\n--- Reviews Related to Anomalies ---")
        for review in analysis_results['reviews_on_date']:
            if review['score'] in analysis_results['anomalies']:
                print(f"\nReview by {review['userName']} (Rating: {review['score']}):")
                print(f"Date: {review['at']}")
                print(f"Review: {review['content']}")
    else:
        print("\nNo anomalies detected.")
        if analysis_results['no_anomaly_reasons']:
            print("Reasons:")
            for reason in analysis_results['no_anomaly_reasons']:
                print(f"- {reason}")

if __name__ == "__main__":
    main()

