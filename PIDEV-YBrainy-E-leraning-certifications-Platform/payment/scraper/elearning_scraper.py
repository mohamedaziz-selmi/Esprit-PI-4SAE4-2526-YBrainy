import json
import csv
import time
import random
import os
from datetime import datetime, timedelta
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from seleniumbase import SB
from bs4 import BeautifulSoup
import requests
from urllib.parse import urlencode, urljoin, quote_plus
import re
import numpy as np
from typing import List, Dict, Any, Optional
import logging
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor, as_completed
import sqlite3

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler()]
)


@dataclass
class CourseMetrics:
    enrollment_count: int = 0
    demand_level: str = "unknown"
    salary_impact: str = "unknown"
    market_saturation: float = 0.0
    roi_estimate: float = 0.0
    trending_factor: float = 1.0
    job_postings_count: int = 0


class ELearningIntelligenceScraper:
    def __init__(self):
        self.course_data = []
        self.certification_data = []
        self.skill_trends = []
        self.failed_requests = []
        self.success_rate = 0.0
        self.screenshots_saved = 0
        self.js_errors_detected = []
        self.page_load_times = []

        self.output_base_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "result")

        self.skill_categories = {
            'python': {'weight': 0.95, 'avg_salary_boost': 25000, 'demand': 'very_high'},
            'javascript': {'weight': 0.9, 'avg_salary_boost': 22000, 'demand': 'very_high'},
            'react': {'weight': 0.88, 'avg_salary_boost': 24000, 'demand': 'very_high'},
            'machine-learning': {'weight': 0.95, 'avg_salary_boost': 35000, 'demand': 'very_high'},
            'data-science': {'weight': 0.93, 'avg_salary_boost': 30000, 'demand': 'very_high'},
            'aws': {'weight': 0.92, 'avg_salary_boost': 28000, 'demand': 'very_high'},
            'docker': {'weight': 0.85, 'avg_salary_boost': 20000, 'demand': 'high'},
            'kubernetes': {'weight': 0.87, 'avg_salary_boost': 25000, 'demand': 'high'},
            'sql': {'weight': 0.88, 'avg_salary_boost': 18000, 'demand': 'very_high'},
            'cloud-computing': {'weight': 0.9, 'avg_salary_boost': 27000, 'demand': 'very_high'},
            'tableau': {'weight': 0.82, 'avg_salary_boost': 15000, 'demand': 'high'},
            'power-bi': {'weight': 0.83, 'avg_salary_boost': 16000, 'demand': 'high'},
            'excel': {'weight': 0.75, 'avg_salary_boost': 12000, 'demand': 'high'},
            'data-analysis': {'weight': 0.85, 'avg_salary_boost': 20000, 'demand': 'very_high'},
            'statistics': {'weight': 0.8, 'avg_salary_boost': 18000, 'demand': 'high'},
            'project-management': {'weight': 0.8, 'avg_salary_boost': 15000, 'demand': 'high'},
            'agile': {'weight': 0.78, 'avg_salary_boost': 14000, 'demand': 'high'},
            'scrum': {'weight': 0.77, 'avg_salary_boost': 13000, 'demand': 'high'},
            'pmp': {'weight': 0.82, 'avg_salary_boost': 18000, 'demand': 'high'},
            'six-sigma': {'weight': 0.75, 'avg_salary_boost': 12000, 'demand': 'medium'},
            'cybersecurity': {'weight': 0.93, 'avg_salary_boost': 32000, 'demand': 'very_high'},
            'ethical-hacking': {'weight': 0.88, 'avg_salary_boost': 28000, 'demand': 'high'},
            'cissp': {'weight': 0.9, 'avg_salary_boost': 30000, 'demand': 'very_high'},
            'comptia': {'weight': 0.82, 'avg_salary_boost': 15000, 'demand': 'high'},
            'ux-design': {'weight': 0.85, 'avg_salary_boost': 22000, 'demand': 'high'},
            'ui-design': {'weight': 0.82, 'avg_salary_boost': 20000, 'demand': 'high'},
            'graphic-design': {'weight': 0.72, 'avg_salary_boost': 12000, 'demand': 'medium'},
            'video-editing': {'weight': 0.7, 'avg_salary_boost': 10000, 'demand': 'medium'},
            'digital-marketing': {'weight': 0.8, 'avg_salary_boost': 15000, 'demand': 'high'},
            'seo': {'weight': 0.75, 'avg_salary_boost': 12000, 'demand': 'high'},
            'google-analytics': {'weight': 0.78, 'avg_salary_boost': 14000, 'demand': 'high'},
            'social-media': {'weight': 0.7, 'avg_salary_boost': 10000, 'demand': 'medium'},
            'artificial-intelligence': {'weight': 0.98, 'avg_salary_boost': 40000, 'demand': 'very_high'},
            'deep-learning': {'weight': 0.95, 'avg_salary_boost': 38000, 'demand': 'very_high'},
            'nlp': {'weight': 0.92, 'avg_salary_boost': 35000, 'demand': 'high'},
            'blockchain': {'weight': 0.85, 'avg_salary_boost': 25000, 'demand': 'medium'},
            'prompt-engineering': {'weight': 0.88, 'avg_salary_boost': 28000, 'demand': 'high'}
        }

        self.user_agents = [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        ]

        self.roi_indicators = {
            'high_demand': {
                'keywords': ['certification', 'professional', 'advanced', 'expert', 'master', 'specialist',
                             'aws', 'google', 'microsoft', 'cisco', 'comptia', 'pmp', 'cissp'],
                'weight': 30
            },
            'career_boost': {
                'keywords': ['career', 'job-ready', 'placement', 'bootcamp', 'nanodegree', 'professional certificate',
                             'salary', 'promotion', 'senior', 'lead'],
                'weight': 25
            },
            'trending_tech': {
                'keywords': ['ai', 'machine learning', 'data science', 'cloud', 'devops', 'cybersecurity',
                             'blockchain', 'automation', 'analytics'],
                'weight': 20
            },
            'practical_skills': {
                'keywords': ['hands-on', 'project-based', 'real-world', 'portfolio', 'capstone',
                             'practical', 'applied', 'industry'],
                'weight': 18
            },
            'recognized_provider': {
                'keywords': ['university', 'stanford', 'mit', 'harvard', 'google', 'ibm', 'amazon',
                             'microsoft', 'meta', 'coursera', 'edx', 'udacity'],
                'weight': 15
            }
        }

        self.setup_database()
        self.create_directories()

        log_file = os.path.join(self.output_base_dir, 'logs', 'elearning_scraper.log')
        file_handler = logging.FileHandler(log_file)
        file_handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
        logging.getLogger().addHandler(file_handler)

    def get_output_path(self, relative_path: str) -> str:
        return os.path.join(self.output_base_dir, relative_path)

    def setup_database(self):
        self.db_path = os.path.join(self.output_base_dir, "elearning_intelligence.db")
        os.makedirs(self.output_base_dir, exist_ok=True)

        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()

        cursor.execute('''
            CREATE TABLE IF NOT EXISTS courses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                platform TEXT,
                price REAL,
                instructor TEXT,
                rating REAL,
                enrollments INTEGER,
                duration_hours REAL,
                skill_level TEXT,
                roi_score REAL,
                demand_score REAL,
                salary_impact INTEGER,
                job_postings INTEGER,
                date_scraped TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                category TEXT,
                url TEXT,
                UNIQUE(title, platform)
            )
        ''')

        cursor.execute('''
            CREATE TABLE IF NOT EXISTS certifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                provider TEXT,
                exam_cost REAL,
                prep_time_hours REAL,
                pass_rate REAL,
                validity_years INTEGER,
                renewal_required BOOLEAN,
                roi_score REAL,
                job_demand INTEGER,
                avg_salary INTEGER,
                date_scraped TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                category TEXT,
                url TEXT,
                UNIQUE(title, provider)
            )
        ''')

        cursor.execute('''
            CREATE TABLE IF NOT EXISTS skill_trends (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                skill_name TEXT NOT NULL,
                trend_score REAL,
                job_postings INTEGER,
                growth_rate REAL,
                avg_salary INTEGER,
                source TEXT,
                date_scraped TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(skill_name, source, date_scraped)
            )
        ''')

        conn.commit()
        conn.close()

    def create_directories(self):
        directories = [
            "elearning_outputs", "roi_analysis",
            "visualizations", "reports", "logs", "elearning_outputs/screenshots"
        ]
        for directory in directories:
            full_path = os.path.join(self.output_base_dir, directory)
            if not os.path.exists(full_path):
                os.makedirs(full_path)

    def enhanced_delay(self, base_delay=3, max_delay=8):
        delay = random.uniform(base_delay, max_delay)
        if random.random() < 0.1:
            delay *= 2
        time.sleep(delay)

    def get_enhanced_headers(self, referer=None):
        headers = {
            'User-Agent': random.choice(self.user_agents),
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
            'Accept-Language': 'en-US,en;q=0.9',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'none',
            'Sec-Fetch-User': '?1',
            'Cache-Control': 'max-age=0',
            'sec-ch-ua': '"Not A(Brand";v="99", "Google Chrome";v="121", "Chromium";v="121"',
            'sec-ch-ua-mobile': '?0',
            'sec-ch-ua-platform': '"Windows"'
        }

        if referer:
            headers['Referer'] = referer

        return headers

    def calculate_enhanced_roi_score(self, course_data: Dict[str, Any]) -> Dict[str, Any]:
        scores = {
            'base_score': 30.0,
            'demand_score': 0.0,
            'skill_category_score': 0.0,
            'keyword_score': 0.0,
            'engagement_score': 0.0,
            'provider_score': 0.0,
            'price_value_score': 0.0
        }

        title = course_data.get('title', '').lower()
        description = course_data.get('description', '').lower()
        platform = course_data.get('platform', '').lower()
        content = f"{title} {description} {platform}"

        for skill, data in self.skill_categories.items():
            if skill in content or skill.replace('-', ' ') in content:
                scores['skill_category_score'] = data['weight'] * 30
                course_data['estimated_salary_boost'] = data['avg_salary_boost']
                course_data['demand_level'] = data['demand']
                break

        for indicator_type, data in self.roi_indicators.items():
            for keyword in data['keywords']:
                if keyword in content:
                    scores['keyword_score'] += data['weight']
                    break

        if 'enrollments' in course_data or 'students' in course_data:
            try:
                enrollments = int(str(course_data.get('enrollments', course_data.get('students', '0'))).replace(',', ''))
                if enrollments > 100000:
                    scores['engagement_score'] = 25
                elif enrollments > 50000:
                    scores['engagement_score'] = 20
                elif enrollments > 10000:
                    scores['engagement_score'] = 15
                elif enrollments > 1000:
                    scores['engagement_score'] = 10
                else:
                    scores['engagement_score'] = 5
            except:
                pass

        if 'rating' in course_data:
            try:
                rating = float(course_data.get('rating', 0))
                if rating >= 4.7:
                    scores['engagement_score'] += 10
                elif rating >= 4.5:
                    scores['engagement_score'] += 8
                elif rating >= 4.0:
                    scores['engagement_score'] += 5
            except:
                pass

        top_providers = ['coursera', 'edx', 'udacity', 'stanford', 'mit', 'harvard', 'google',
                        'ibm', 'amazon', 'microsoft', 'meta', 'linkedin learning']
        for provider in top_providers:
            if provider in content:
                scores['provider_score'] = 15
                break

        price = self.extract_price(course_data.get('price', 0))
        if price == 0:
            scores['price_value_score'] = 20
        elif price > 0:
            estimated_boost = course_data.get('estimated_salary_boost', 15000)
            if price < 100 and estimated_boost > 10000:
                scores['price_value_score'] = 18
            elif price < 500 and estimated_boost > 20000:
                scores['price_value_score'] = 15
            elif price < 1000:
                scores['price_value_score'] = 10
            else:
                scores['price_value_score'] = 5

        total_score = sum(scores.values())
        total_score = min(100.0, max(0.0, total_score))

        return {
            'total_score': round(total_score, 2),
            'score_breakdown': scores
        }

    def extract_price(self, price_text) -> float:
        if not price_text or price_text == "N/A" or price_text == "Free":
            return 0.0

        price_patterns = [
            r'\$(\d+(?:,\d{3})*\.?\d*)',
            r'(\d+(?:,\d{3})*\.?\d*)\s*\$',
            r'USD\s*(\d+(?:,\d{3})*\.?\d*)',
            r'(\d+(?:,\d{3})*\.?\d*)\s*USD',
            r'(\d+(?:\.\d{2})?)',
        ]

        price_str = str(price_text).strip()
        for pattern in price_patterns:
            match = re.search(pattern, price_str)
            if match:
                try:
                    return float(match.group(1).replace(',', ''))
                except:
                    continue
        return 0.0

    def scrape_udemy_enhanced(self, skill_queries: List[str], max_pages=3):
        for query in skill_queries:
            logging.info(f"Analyzing Udemy courses for: {query}")

            api_success = self.scrape_udemy_api_approach(query)

            if api_success:
                logging.info(f"Successfully used API approach for {query}")
                self.enhanced_delay(3, 6)
                continue

            logging.info(f"Falling back to browser scraping for {query}")

            try:
                with SB(uc=True, test=True, headless=False,
                        ad_block=True, do_not_track=True, incognito=True,
                        agent=random.choice(self.user_agents),
                        locale="en", timeout_multiplier=3.0) as sb:

                    for page in range(1, max_pages + 1):
                        try:
                            search_url = f"https://www.udemy.com/courses/search/?q={quote_plus(query)}&p={page}"

                            start_time = time.time()

                            sb.open(search_url)
                            sb.sleep(5)

                            for scroll in range(3):
                                sb.execute_script("window.scrollBy(0, 500);")
                                sb.sleep(1)

                            sb.wait_for_ready_state_complete()

                            if not sb.is_element_present("body"):
                                logging.warning(f"Page failed to load for {query} page {page}")
                                break

                            load_time = time.time() - start_time
                            self.page_load_times.append({
                                'platform': 'Udemy',
                                'query': query,
                                'page': page,
                                'load_time': load_time
                            })

                            debug_screenshot = self.get_output_path(f"elearning_outputs/screenshots/debug_udemy_{query.replace(' ', '_')}_page_{page}.png")
                            sb.save_screenshot(debug_screenshot)
                            logging.info(f"Debug screenshot saved: {debug_screenshot}")

                            courses_data = self.extract_udemy_courses(sb, query, page)

                            valid_courses = 0
                            for course_data in courses_data:
                                if self.validate_course_data(course_data):
                                    self.course_data.append(course_data)
                                    self.save_to_database(course_data, 'course')
                                    valid_courses += 1

                            logging.info(f"Udemy page {page}: {valid_courses}/{len(courses_data)} valid courses for {query}")

                            if valid_courses > 5:
                                screenshot_path = self.get_output_path(f"elearning_outputs/screenshots/udemy_{query.replace(' ', '_')}_page_{page}.png")
                                sb.save_screenshot(screenshot_path)
                                self.screenshots_saved += 1

                            if valid_courses == 0:
                                logging.warning("No valid courses found, trying next page or ending search")
                                self.enhanced_delay(8, 15)
                                if page >= 2:
                                    break
                            else:
                                self.enhanced_delay(5, 10)

                        except Exception as e:
                            logging.error(f"Error on Udemy page {page} for {query}: {str(e)}")

                            try:
                                error_screenshot = self.get_output_path(f"elearning_outputs/screenshots/error_udemy_{query.replace(' ', '_')}_page_{page}.png")
                                sb.save_screenshot(error_screenshot)
                                logging.info(f"Error screenshot saved: {error_screenshot}")
                            except:
                                pass
                            break

            except Exception as e:
                logging.error(f"Fatal Udemy setup error for {query}: {str(e)}")
                self.failed_requests.append(f"Udemy: {query}")

    def scrape_udemy_api_approach(self, query: str) -> bool:
        try:
            headers = self.get_enhanced_headers()
            headers['Accept'] = 'application/json, text/plain, */*'

            search_url = f"https://www.udemy.com/api-2.0/search-courses/?q={quote_plus(query)}&p=1&size=12"

            response = requests.get(search_url, headers=headers, timeout=15)

            if response.status_code == 200:
                try:
                    data = response.json()
                    courses = data.get('results', [])

                    if courses:
                        logging.info(f"API approach found {len(courses)} courses for {query}")

                        for course in courses[:15]:
                            course_data = {
                                "platform": "Udemy",
                                "title": course.get('title', ''),
                                "price": course.get('price_detail', {}).get('amount', 0),
                                "rating": course.get('rating', 0),
                                "enrollments": course.get('num_subscribers', 0),
                                "instructor": course.get('visible_instructors', [{}])[0].get('display_name', '') if course.get('visible_instructors') else '',
                                "url": f"https://www.udemy.com{course.get('url', '')}",
                                "query": query,
                                "date_scraped": datetime.now().isoformat(),
                                "extraction_method": "api"
                            }

                            roi_analysis = self.calculate_enhanced_roi_score(course_data)
                            course_data["roi_score"] = roi_analysis['total_score']
                            course_data["score_breakdown"] = roi_analysis['score_breakdown']
                            course_data["skill_category"] = self.classify_skill_category(course_data)

                            if self.validate_course_data(course_data):
                                self.course_data.append(course_data)
                                self.save_to_database(course_data, 'course')

                        return True
                except Exception as e:
                    logging.warning(f"API response parsing failed: {str(e)}")
                    return False
            else:
                logging.warning(f"API returned status {response.status_code}")
                return False

        except Exception as e:
            logging.warning(f"API approach failed: {str(e)}")
            return False

    def extract_udemy_courses(self, sb, query: str, page: int) -> List[Dict[str, Any]]:
        courses_data = []

        from bs4 import BeautifulSoup

        selector_strategies = [
            {
                'container': "[data-purpose='course-card-container']",
                'title': "h3",
                'price': "[data-purpose='course-price-text']",
                'rating': "span[data-purpose='rating-number']",
                'students': "span[data-purpose='enrollment']"
            },
            {
                'container': "div[class*='course-card']",
                'title': "h3 a, div[class*='course-title']",
                'price': "div[class*='price'], span[class*='price']",
                'rating': "span[class*='rating']",
                'students': "span[class*='enrollment'], span[class*='student']"
            },
            {
                'container': "a[class*='course-card']",
                'title': "h3, div[class*='title']",
                'price': "div[data-purpose*='price'], span[data-purpose*='price']",
                'rating': "div[class*='star'], span[class*='star']",
                'students': "span"
            },
            {
                'container': "div.popper-module--popper-content--",
                'title': "h3",
                'price': "span",
                'rating': "span",
                'students': "span"
            }
        ]

        try:
            page_source = sb.get_page_source()

            debug_file = self.get_output_path(f"elearning_outputs/debug_udemy_{query.replace(' ', '_')}_page_{page}.html")
            with open(debug_file, 'w', encoding='utf-8') as f:
                f.write(page_source)
            logging.info(f"Saved page source to {debug_file} for debugging")

            if 'udemy' not in page_source.lower():
                logging.error("Page doesn't appear to be Udemy - possible bot detection")
                return courses_data

        except Exception as e:
            logging.warning(f"Could not save debug info: {str(e)}")

        courses_found = []
        strategy_used = None

        for i, strategy in enumerate(selector_strategies):
            try:
                if sb.is_element_present(strategy['container']):
                    sb.wait_for_element(strategy['container'], timeout=15)
                    courses_found = sb.find_elements(strategy['container'])
                    if courses_found:
                        strategy_used = strategy
                        logging.info(f"Using Udemy selector strategy {i + 1} - found {len(courses_found)} courses")
                        break
            except Exception as e:
                logging.warning(f"Udemy selector strategy {i + 1} failed: {str(e)}")
                continue

        if not courses_found:
            logging.error(f"No courses found with any selector strategy for {query} page {page}")
            logging.error("Trying alternative: parse entire page for course data...")

            try:
                from bs4 import BeautifulSoup
                soup = BeautifulSoup(sb.get_page_source(), 'html.parser')

                course_links = soup.find_all('a', href=re.compile(r'/course/'))
                logging.info(f"Found {len(course_links)} course links in page")

                for link in course_links[:15]:
                    try:
                        title_elem = link.find('h3') or link.find('div', class_=re.compile(r'title'))
                        if title_elem:
                            course_data = {
                                "platform": "Udemy",
                                "title": title_elem.get_text(strip=True),
                                "url": "https://www.udemy.com" + link.get('href', ''),
                                "query": query,
                                "page": page,
                                "date_scraped": datetime.now().isoformat(),
                                "extraction_method": "fallback_html_parse"
                            }

                            parent = link.find_parent()
                            if parent:
                                price_elem = parent.find('span', class_=re.compile(r'price'))
                                if price_elem:
                                    course_data['price'] = self.extract_price(price_elem.get_text(strip=True))

                            roi_analysis = self.calculate_enhanced_roi_score(course_data)
                            course_data["roi_score"] = roi_analysis['total_score']
                            course_data["score_breakdown"] = roi_analysis['score_breakdown']
                            course_data["skill_category"] = self.classify_skill_category(course_data)

                            courses_data.append(course_data)
                    except Exception as e:
                        continue

                if courses_data:
                    logging.info(f"Fallback extraction found {len(courses_data)} courses")
                    return courses_data

            except Exception as e:
                logging.error(f"Fallback extraction also failed: {str(e)}")

            return courses_data

        for idx, course in enumerate(courses_found[:15]):
            try:
                course_html = course.get_attribute('outerHTML')
                soup = BeautifulSoup(course_html, 'html.parser')

                course_data = {
                    "platform": "Udemy",
                    "query": query,
                    "page": page,
                    "position": idx + 1,
                    "date_scraped": datetime.now().isoformat()
                }

                course_data.update(self.extract_udemy_course_details(soup, strategy_used))

                if course_data.get('title') and len(course_data.get('title', '')) > 10:
                    roi_analysis = self.calculate_enhanced_roi_score(course_data)
                    course_data["roi_score"] = roi_analysis['total_score']
                    course_data["score_breakdown"] = roi_analysis['score_breakdown']
                    course_data["skill_category"] = self.classify_skill_category(course_data)

                    courses_data.append(course_data)

            except Exception as e:
                logging.warning(f"Failed to extract Udemy course {idx + 1}: {str(e)}")
                continue

        return courses_data

    def extract_udemy_course_details(self, soup: BeautifulSoup, strategy: Dict[str, str]) -> Dict[str, Any]:
        course_data = {}

        title_selectors = [
            strategy.get('title', ''),
            "[data-purpose='course-title-url']",
            ".course-card--course-title--",
            "h3 a"
        ]

        for selector in title_selectors:
            if not selector:
                continue
            title_elem = soup.select_one(selector)
            if title_elem and title_elem.get_text(strip=True):
                course_data["title"] = title_elem.get_text(strip=True)
                if title_elem.get('href'):
                    course_data["url"] = "https://www.udemy.com" + title_elem.get('href')
                break

        price_selectors = [
            strategy.get('price', ''),
            "[data-purpose='course-price-text']",
            ".price-text--price-part--",
            ".price"
        ]

        for selector in price_selectors:
            if not selector:
                continue
            price_elem = soup.select_one(selector)
            if price_elem:
                price_text = price_elem.get_text(strip=True)
                if 'free' in price_text.lower():
                    course_data["price"] = 0.0
                    course_data["price_text"] = "Free"
                else:
                    price_value = self.extract_price(price_text)
                    course_data["price"] = price_value
                    course_data["price_text"] = price_text
                break

        rating_selectors = [
            strategy.get('rating', ''),
            "[data-purpose='rating']",
            ".star-rating--rating-number--"
        ]

        for selector in rating_selectors:
            if not selector:
                continue
            rating_elem = soup.select_one(selector)
            if rating_elem:
                rating_text = rating_elem.get_text(strip=True)
                rating_match = re.search(r'(\d+\.?\d*)', rating_text)
                if rating_match:
                    try:
                        rating_value = float(rating_match.group(1))
                        if 0 <= rating_value <= 5:
                            course_data["rating"] = rating_value
                            break
                    except:
                        continue

        student_selectors = [
            strategy.get('students', ''),
            "[data-purpose='enrollment']",
            ".course-card--student-count--"
        ]

        for selector in student_selectors:
            if not selector:
                continue
            student_elem = soup.select_one(selector)
            if student_elem:
                student_text = student_elem.get_text(strip=True)
                student_match = re.search(r'(\d{1,3}(?:,\d{3})*)', student_text)
                if student_match:
                    try:
                        enrollments = int(student_match.group(1).replace(',', ''))
                        course_data["enrollments"] = enrollments
                        break
                    except:
                        continue

        return course_data

    def scrape_coursera_enhanced(self, skill_queries: List[str], max_pages=2):
        for query in skill_queries:
            logging.info(f"Analyzing Coursera courses for: {query}")

            try:
                with SB(uc=True, test=True, headless=False,
                        ad_block=True, do_not_track=True, incognito=True,
                        agent=random.choice(self.user_agents)) as sb:

                    search_url = f"https://www.coursera.org/search?query={quote_plus(query)}"

                    start_time = time.time()
                    sb.activate_cdp_mode(search_url)
                    sb.sleep(4)

                    for scroll in range(3):
                        sb.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                        sb.sleep(2)

                    load_time = time.time() - start_time
                    self.page_load_times.append({
                        'platform': 'Coursera',
                        'query': query,
                        'load_time': load_time
                    })

                    courses_data = self.extract_coursera_courses(sb, query)

                    valid_courses = 0
                    for course_data in courses_data:
                        if self.validate_course_data(course_data):
                            self.course_data.append(course_data)
                            self.save_to_database(course_data, 'course')
                            valid_courses += 1

                    logging.info(f"Coursera: {valid_courses}/{len(courses_data)} valid courses for {query}")

                    if valid_courses > 5:
                        screenshot_path = self.get_output_path(f"elearning_outputs/screenshots/coursera_{query.replace(' ', '_')}.png")
                        sb.save_screenshot(screenshot_path)
                        self.screenshots_saved += 1

                    self.enhanced_delay(6, 12)

            except Exception as e:
                logging.error(f"Coursera error for {query}: {str(e)}")
                self.failed_requests.append(f"Coursera: {query}")

    def extract_coursera_courses(self, sb, query: str) -> List[Dict[str, Any]]:
        courses_data = []

        selector_strategies = [
            {
                'container': "[data-e2e='SearchResults'] [data-click-key='search.search.click.search_card']",
                'title': "h3",
                'provider': "[data-e2e='product-source']",
                'rating': "[data-e2e='product-reviews']"
            },
            {
                'container': ".cds-ProductCard-base",
                'title': ".cds-ProductCard-title",
                'provider': ".cds-ProductCard-partnerNames",
                'rating': ".cds-ProductCard-rating"
            }
        ]

        courses_found = []
        strategy_used = None

        for i, strategy in enumerate(selector_strategies):
            try:
                if sb.is_element_present(strategy['container']):
                    courses_found = sb.find_elements(strategy['container'])
                    if courses_found:
                        strategy_used = strategy
                        logging.info(f"Using Coursera strategy {i + 1} - found {len(courses_found)} courses")
                        break
            except Exception as e:
                logging.warning(f"Coursera strategy {i + 1} failed: {str(e)}")
                continue

        for idx, course in enumerate(courses_found[:15]):
            try:
                course_html = course.get_attribute('outerHTML')
                soup = BeautifulSoup(course_html, 'html.parser')

                course_data = {
                    "platform": "Coursera",
                    "query": query,
                    "position": idx + 1,
                    "date_scraped": datetime.now().isoformat()
                }

                title_elem = soup.select_one(strategy_used.get('title', 'h3'))
                if title_elem:
                    course_data["title"] = title_elem.get_text(strip=True)

                provider_elem = soup.select_one(strategy_used.get('provider', ''))
                if provider_elem:
                    course_data["instructor"] = provider_elem.get_text(strip=True)

                rating_elem = soup.select_one(strategy_used.get('rating', ''))
                if rating_elem:
                    rating_text = rating_elem.get_text(strip=True)
                    rating_match = re.search(r'(\d+\.?\d*)', rating_text)
                    if rating_match:
                        try:
                            course_data["rating"] = float(rating_match.group(1))
                        except:
                            pass

                course_data["price"] = 0.0
                course_data["price_text"] = "Free to audit / Paid certificate"

                if course_data.get('title'):
                    roi_analysis = self.calculate_enhanced_roi_score(course_data)
                    course_data["roi_score"] = roi_analysis['total_score']
                    course_data["score_breakdown"] = roi_analysis['score_breakdown']
                    course_data["skill_category"] = self.classify_skill_category(course_data)

                    courses_data.append(course_data)

            except Exception as e:
                logging.warning(f"Failed to extract Coursera course {idx + 1}: {str(e)}")
                continue

        return courses_data

    def scrape_reddit_learning_trends(self, subreddits: List[str]):
        for subreddit in subreddits:
            logging.info(f"Analyzing r/{subreddit} for career & learning trends...")

            url = f"https://www.reddit.com/r/{subreddit}/top/?t=month"

            with SB(uc=True, headless=False, agent=random.choice(self.user_agents)) as sb:
                try:
                    sb.open(url)
                    sb.wait_for_element("h3, [data-testid='post-content']", timeout=20)

                    for scroll in range(5):
                        sb.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                        sb.sleep(2)

                    content = sb.get_page_source()
                    soup = BeautifulSoup(content, 'html.parser')

                    post_selectors = [
                        'h3[slot="title"]',
                        '[data-testid="post-content"] h3',
                        '.Post h3',
                        'h3 a'
                    ]

                    posts = []
                    for selector in post_selectors:
                        posts = soup.select(selector)
                        if posts:
                            break

                    extracted_count = 0
                    for post in posts[:20]:
                        try:
                            title = post.get_text(strip=True)

                            if len(title) < 15:
                                continue

                            post_element = post.find_parent()
                            upvotes = 0
                            comments = 0

                            vote_elements = post_element.select('[aria-label*="upvote"], .vote, [data-testid*="vote"]')
                            for vote_elem in vote_elements:
                                vote_text = vote_elem.get_text(strip=True)
                                vote_match = re.search(r'(\d+)', vote_text)
                                if vote_match:
                                    upvotes = int(vote_match.group(1))
                                    break

                            comment_elements = post_element.select('[aria-label*="comment"], .comment, [data-testid*="comment"]')
                            for comment_elem in comment_elements:
                                comment_text = comment_elem.get_text(strip=True)
                                comment_match = re.search(r'(\d+)', comment_text)
                                if comment_match:
                                    comments = int(comment_match.group(1))
                                    break

                            trend_data = {
                                "title": title,
                                "source": f"Reddit r/{subreddit}",
                                "platform": "Reddit",
                                "upvotes": upvotes,
                                "comments": comments,
                                "engagement_score": upvotes + (comments * 2),
                                "date_scraped": datetime.now().isoformat(),
                                "subreddit": subreddit
                            }

                            roi_analysis = self.calculate_enhanced_roi_score(trend_data)
                            trend_data["roi_score"] = roi_analysis['total_score']
                            trend_data["score_breakdown"] = roi_analysis['score_breakdown']
                            trend_data["skill_category"] = self.classify_skill_category(trend_data)

                            self.skill_trends.append(trend_data)
                            self.save_to_database(trend_data, 'trend')
                            extracted_count += 1

                        except Exception as e:
                            logging.error(f"Error processing Reddit post: {str(e)}")
                            continue

                    logging.info(f"Extracted {extracted_count} learning trends from r/{subreddit}")
                    self.enhanced_delay(4, 8)

                except Exception as e:
                    logging.error(f"Error scraping r/{subreddit}: {str(e)}")
                    self.failed_requests.append(f"Reddit: r/{subreddit}")

    def scrape_freecodecamp_trends(self):
        logging.info("Analyzing freeCodeCamp for trending tech skills...")

        try:
            with SB(uc=True, headless=False, agent=random.choice(self.user_agents)) as sb:
                url = "https://www.freecodecamp.org/news/"
                sb.open(url)
                sb.sleep(3)

                for scroll in range(3):
                    sb.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                    sb.sleep(2)

                page_source = sb.get_page_source()
                soup = BeautifulSoup(page_source, 'html.parser')

                articles = soup.find_all('a', class_='post-card-content-link')

                extracted_count = 0
                for article in articles[:20]:
                    try:
                        title = article.get_text(strip=True)

                        if len(title) < 15:
                            continue

                        trend_data = {
                            "title": title,
                            "platform": "freeCodeCamp",
                            "source": "freeCodeCamp News",
                            "url": article.get('href', ''),
                            "date_scraped": datetime.now().isoformat(),
                            "extraction_method": "content_analysis"
                        }

                        roi_analysis = self.calculate_enhanced_roi_score(trend_data)
                        trend_data["roi_score"] = roi_analysis['total_score']
                        trend_data["score_breakdown"] = roi_analysis['score_breakdown']
                        trend_data["skill_category"] = self.classify_skill_category(trend_data)

                        self.skill_trends.append(trend_data)
                        self.save_to_database(trend_data, 'trend')
                        extracted_count += 1

                    except Exception as e:
                        logging.warning(f"Error processing freeCodeCamp article: {str(e)}")
                        continue

                logging.info(f"Extracted {extracted_count} skill trends from freeCodeCamp")
                return True

        except Exception as e:
            logging.error(f"Error scraping freeCodeCamp: {str(e)}")
            return False

    def scrape_youtube_learning_trends(self, topics: List[str]):
        for topic in topics:
            logging.info(f"Analyzing YouTube learning trends for: {topic}")

            try:
                with SB(uc=True, headless=False, agent=random.choice(self.user_agents)) as sb:
                    search_url = f"https://www.youtube.com/results?search_query={quote_plus(topic + ' tutorial')}"

                    sb.open(search_url)
                    sb.sleep(4)

                    for scroll in range(2):
                        sb.execute_script("window.scrollBy(0, 1000);")
                        sb.sleep(2)

                    page_source = sb.get_page_source()
                    soup = BeautifulSoup(page_source, 'html.parser')

                    video_renderers = soup.find_all('ytd-video-renderer') or soup.find_all('a', id='video-title')

                    extracted_count = 0
                    for video in video_renderers[:10]:
                        try:
                            title_elem = video.find('a', id='video-title') or video.find('h3')
                            if not title_elem:
                                continue

                            title = title_elem.get('title') or title_elem.get_text(strip=True)

                            if len(title) < 10:
                                continue

                            views = 0
                            view_elem = video.find('span', class_='inline-metadata-item')
                            if view_elem:
                                view_text = view_elem.get_text(strip=True)
                                view_match = re.search(r'(\d+(?:\.\d+)?)\s*([KMB]?)', view_text)
                                if view_match:
                                    num = float(view_match.group(1))
                                    multiplier = view_match.group(2)
                                    if multiplier == 'K':
                                        views = int(num * 1000)
                                    elif multiplier == 'M':
                                        views = int(num * 1000000)
                                    elif multiplier == 'B':
                                        views = int(num * 1000000000)
                                    else:
                                        views = int(num)

                            trend_data = {
                                "title": title,
                                "platform": "YouTube",
                                "source": f"YouTube - {topic}",
                                "views": views,
                                "engagement_score": views // 100,
                                "date_scraped": datetime.now().isoformat()
                            }

                            roi_analysis = self.calculate_enhanced_roi_score(trend_data)
                            trend_data["roi_score"] = roi_analysis['total_score']
                            trend_data["score_breakdown"] = roi_analysis['score_breakdown']
                            trend_data["skill_category"] = self.classify_skill_category(trend_data)

                            self.skill_trends.append(trend_data)
                            self.save_to_database(trend_data, 'trend')
                            extracted_count += 1

                        except Exception as e:
                            logging.warning(f"Error processing YouTube video: {str(e)}")
                            continue

                    logging.info(f"Extracted {extracted_count} learning trends from YouTube for {topic}")
                    self.enhanced_delay(3, 6)

            except Exception as e:
                logging.error(f"Error scraping YouTube for {topic}: {str(e)}")
                continue

    def classify_skill_category(self, data: Dict[str, Any]) -> str:
        content = f"{data.get('title', '')} {data.get('description', '')}".lower()

        category_scores = {}
        for skill in self.skill_categories:
            skill_term = skill.replace('-', ' ')
            if skill in content or skill_term in content:
                occurrences = content.count(skill) + content.count(skill_term)
                category_scores[skill] = occurrences

        if category_scores:
            return max(category_scores, key=category_scores.get)

        tech_keywords = ['programming', 'coding', 'development', 'software', 'web', 'app']
        data_keywords = ['data', 'analytics', 'statistics', 'sql', 'tableau']
        business_keywords = ['management', 'business', 'leadership', 'finance', 'marketing']

        if any(kw in content for kw in tech_keywords):
            return 'programming'
        elif any(kw in content for kw in data_keywords):
            return 'data-analysis'
        elif any(kw in content for kw in business_keywords):
            return 'project-management'

        return 'general'

    def validate_course_data(self, data: Dict[str, Any]) -> bool:
        if not data or not data.get("title"):
            return False

        title = data.get("title", "").strip()

        if len(title) < 10:
            return False

        spam_words = ['test', 'sample', 'dummy', 'placeholder', 'lorem ipsum', 'coming soon']
        if any(spam_word in title.lower() for spam_word in spam_words):
            return False

        score = data.get("roi_score", 0)
        if score < 25:
            return False

        return True

    def save_to_database(self, data: Dict[str, Any], data_type: str):
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()

            if data_type == 'course':
                cursor.execute('''
                    INSERT OR REPLACE INTO courses 
                    (title, platform, price, instructor, rating, enrollments, 
                     roi_score, category, url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''', (
                    data.get('title', ''),
                    data.get('platform', ''),
                    data.get('price', 0),
                    data.get('instructor', ''),
                    data.get('rating', 0),
                    data.get('enrollments', 0),
                    data.get('roi_score', 0),
                    data.get('skill_category', ''),
                    data.get('url', '')
                ))

            elif data_type == 'trend':
                cursor.execute('''
                    INSERT OR REPLACE INTO skill_trends 
                    (skill_name, trend_score, source)
                    VALUES (?, ?, ?)
                ''', (
                    data.get('title', ''),
                    data.get('roi_score', 0),
                    data.get('source', '')
                ))

            conn.commit()
            conn.close()

        except Exception as e:
            logging.error(f"Database save error: {str(e)}")

    def generate_ultimate_intelligence_report(self):
        if not self.course_data and not self.skill_trends:
            logging.warning("No data available for report generation")
            return

        logging.info("Generating E-Learning Intelligence Report...")

        df_courses = pd.DataFrame(self.course_data) if self.course_data else pd.DataFrame()
        df_trends = pd.DataFrame(self.skill_trends) if self.skill_trends else pd.DataFrame()
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        total_data_points = len(self.course_data) + len(self.skill_trends)
        total_attempts = total_data_points + len(self.failed_requests)
        self.success_rate = (total_data_points / total_attempts * 100) if total_attempts > 0 else 0

        ultimate_report = {
            'executive_summary': {
                'report_timestamp': timestamp,
                'total_courses_analyzed': len(df_courses),
                'total_trends_analyzed': len(df_trends),
                'high_roi_courses': len(df_courses[df_courses['roi_score'] > 75]) if not df_courses.empty else 0,
                'ultra_high_roi': len(df_courses[df_courses['roi_score'] > 85]) if not df_courses.empty else 0,
                'success_rate_percentage': round(self.success_rate, 2),
                'failed_requests': len(self.failed_requests),
                'average_roi_score': round(df_courses['roi_score'].mean(), 2) if not df_courses.empty else 0,
                'screenshots_captured': self.screenshots_saved
            },
            'roi_analysis': {},
            'market_intelligence': {},
            'career_insights': {},
            'trending_skills': {},
            'action_items': {}
        }

        if not df_courses.empty:
            ultimate_report['roi_analysis'] = self.analyze_course_roi(df_courses)
            ultimate_report['market_intelligence'] = self.analyze_market_intelligence(df_courses)
            ultimate_report['career_insights'] = self.generate_career_insights(df_courses)

        if not df_trends.empty:
            ultimate_report['trending_skills'] = self.analyze_trending_skills(df_trends)

        ultimate_report['action_items'] = self.generate_learning_action_items(df_courses, df_trends)

        report_filename = self.get_output_path(f"reports/elearning_intelligence_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        with open(report_filename, 'w') as f:
            json.dump(ultimate_report, f, indent=2, default=str)

        self.print_intelligence_summary(ultimate_report)
        return ultimate_report

    def analyze_course_roi(self, df: pd.DataFrame) -> Dict[str, Any]:
        roi_analysis = {
            'score_distribution': {
                'Ultra High ROI (85-100)': len(df[df['roi_score'] >= 85]),
                'High ROI (70-84)': len(df[(df['roi_score'] >= 70) & (df['roi_score'] < 85)]),
                'Medium ROI (50-69)': len(df[(df['roi_score'] >= 50) & (df['roi_score'] < 70)]),
                'Low ROI (30-49)': len(df[(df['roi_score'] >= 30) & (df['roi_score'] < 50)])
            },
            'by_platform': df.groupby('platform').agg({
                'roi_score': ['mean', 'count', 'max']
            }).round(2).to_dict() if 'platform' in df.columns else {},
            'by_skill_category': df.groupby('skill_category').agg({
                'roi_score': ['mean', 'count']
            }).round(2).to_dict() if 'skill_category' in df.columns else {},
            'top_rated_courses': df.nlargest(10, 'rating')[['title', 'platform', 'rating', 'roi_score']].to_dict('records') if 'rating' in df.columns else []
        }

        return roi_analysis

    def analyze_market_intelligence(self, df: pd.DataFrame) -> Dict[str, Any]:
        intelligence = {
            'most_popular_platforms': df['platform'].value_counts().head(5).to_dict() if 'platform' in df.columns else {},
            'price_analysis': {
                'free_courses': len(df[df['price'] == 0]) if 'price' in df.columns else 0,
                'paid_courses': len(df[df['price'] > 0]) if 'price' in df.columns else 0,
                'avg_price': round(df[df['price'] > 0]['price'].mean(), 2) if 'price' in df.columns and len(df[df['price'] > 0]) > 0 else 0
            },
            'high_demand_skills': self.identify_high_demand_skills(df)
        }

        return intelligence

    def identify_high_demand_skills(self, df: pd.DataFrame) -> List[Dict[str, Any]]:
        if 'skill_category' not in df.columns:
            return []

        skill_analysis = df.groupby('skill_category').agg({
            'roi_score': 'mean',
            'title': 'count'
        }).rename(columns={'title': 'course_count'})

        high_demand = []
        for skill in skill_analysis.index:
            if skill in self.skill_categories:
                skill_info = self.skill_categories[skill]
                high_demand.append({
                    'skill': skill,
                    'avg_roi_score': round(skill_analysis.loc[skill, 'roi_score'], 2),
                    'course_count': int(skill_analysis.loc[skill, 'course_count']),
                    'estimated_salary_boost': skill_info['avg_salary_boost'],
                    'demand_level': skill_info['demand']
                })

        return sorted(high_demand, key=lambda x: x['avg_roi_score'], reverse=True)[:10]

    def generate_career_insights(self, df: pd.DataFrame) -> Dict[str, Any]:
        insights = {
            'best_certification_paths': [],
            'high_salary_impact_courses': [],
            'beginner_friendly_high_roi': []
        }

        cert_courses = df[df['title'].str.contains('certificate|certification|certified', case=False, na=False)]
        if not cert_courses.empty:
            top_certs = cert_courses.nlargest(5, 'roi_score')[['title', 'platform', 'roi_score']].to_dict('records')
            insights['best_certification_paths'] = top_certs

        high_salary_skills = ['machine-learning', 'data-science', 'aws', 'cybersecurity', 'artificial-intelligence']
        high_salary_courses = df[df['skill_category'].isin(high_salary_skills)]
        if not high_salary_courses.empty:
            insights['high_salary_impact_courses'] = high_salary_courses.nlargest(5, 'roi_score')[['title', 'platform', 'skill_category', 'roi_score']].to_dict('records')

        return insights

    def analyze_trending_skills(self, df: pd.DataFrame) -> Dict[str, Any]:
        trending = {
            'top_community_trends': df.nlargest(10, 'engagement_score')[['title', 'source', 'engagement_score', 'roi_score']].to_dict('records') if 'engagement_score' in df.columns else [],
            'emerging_skills': [],
            'community_insights': {}
        }

        return trending

    def generate_learning_action_items(self, df_courses: pd.DataFrame, df_trends: pd.DataFrame) -> Dict[str, Any]:
        actions = {
            'immediate_learning_opportunities': [],
            'career_switcher_paths': [],
            'skill_upgrade_priorities': []
        }

        if not df_courses.empty:
            top_courses = df_courses[df_courses['roi_score'] > 75].nlargest(10, 'roi_score')

            for _, course in top_courses.iterrows():
                action_item = {
                    'course': course['title'][:80] + '...' if len(course['title']) > 80 else course['title'],
                    'platform': course['platform'],
                    'roi_score': course['roi_score'],
                    'skill_category': course.get('skill_category', 'Unknown'),
                    'price': course.get('price', 'N/A'),
                    'priority': 'HIGH' if course['roi_score'] > 85 else 'MEDIUM',
                    'next_steps': [
                        'Enroll in course or audit for free',
                        'Complete practical projects',
                        'Build portfolio showcasing skills',
                        'Apply for relevant jobs',
                        'Share certificate on LinkedIn'
                    ],
                    'estimated_salary_boost': course.get('estimated_salary_boost', 'Varies'),
                    'time_investment': 'Self-paced (typically 4-12 weeks)'
                }
                actions['immediate_learning_opportunities'].append(action_item)

        return actions

    def create_intelligence_visualizations(self):
        if not self.course_data:
            logging.warning("No data for visualizations")
            return

        df = pd.DataFrame(self.course_data)

        plt.style.use('dark_background')
        sns.set_palette("viridis")

        fig = plt.figure(figsize=(20, 14))
        gs = fig.add_gridspec(3, 3, hspace=0.3, wspace=0.3)

        ax1 = fig.add_subplot(gs[0, :2])
        ax1.hist(df['roi_score'], bins=25, alpha=0.8, color='cyan', edgecolor='white')
        ax1.axvline(df['roi_score'].mean(), color='red', linestyle='--', linewidth=2,
                    label=f'Mean: {df["roi_score"].mean():.1f}')
        ax1.set_title('COURSE ROI SCORE DISTRIBUTION', fontsize=14, fontweight='bold', color='white')
        ax1.set_xlabel('ROI Score', color='white')
        ax1.set_ylabel('Frequency', color='white')
        ax1.legend()
        ax1.grid(True, alpha=0.3)

        ax2 = fig.add_subplot(gs[0, 2])
        if 'platform' in df.columns:
            platform_scores = df.groupby('platform')['roi_score'].mean().sort_values()
            platform_scores.plot(kind='barh', ax=ax2, color='lime')
            ax2.set_title('PLATFORM PERFORMANCE', fontsize=12, fontweight='bold', color='white')
            ax2.set_xlabel('Avg ROI Score', color='white')

        ax3 = fig.add_subplot(gs[1, :])
        if 'skill_category' in df.columns:
            category_stats = df.groupby('skill_category').agg({
                'roi_score': 'mean',
                'title': 'count'
            }).rename(columns={'title': 'count'})
            category_stats = category_stats.nlargest(10, 'roi_score')

            x = np.arange(len(category_stats))
            width = 0.35

            ax3.bar(x - width/2, category_stats['roi_score'], width, label='Avg ROI Score', color='cyan')
            ax3.bar(x + width/2, category_stats['count'], width, label='Course Count', color='orange')

            ax3.set_xlabel('Skill Category', color='white', fontweight='bold')
            ax3.set_title('TOP 10 SKILL CATEGORIES - ROI & AVAILABILITY', fontsize=14, fontweight='bold', color='lime')
            ax3.set_xticks(x)
            ax3.set_xticklabels(category_stats.index, rotation=45, ha='right', color='white')
            ax3.legend()
            ax3.grid(True, alpha=0.3, axis='y')

        ax4 = fig.add_subplot(gs[2, :])
        top_courses = df.nlargest(12, 'roi_score')

        colors = plt.cm.RdYlGn(top_courses['roi_score'] / 100)
        bars = ax4.barh(range(len(top_courses)), top_courses['roi_score'], color=colors)

        ax4.set_yticks(range(len(top_courses)))
        labels = [f"{course[:50]}..." if len(course) > 50 else course for course in top_courses['title']]
        ax4.set_yticklabels(labels, color='white', fontsize=9)
        ax4.set_xlabel('ROI Score', color='white', fontsize=12)
        ax4.set_title('TOP 12 HIGH-ROI LEARNING OPPORTUNITIES', fontsize=16, fontweight='bold', color='gold', pad=20)

        for i, (idx, course) in enumerate(top_courses.iterrows()):
            score = course['roi_score']
            platform = course['platform']
            ax4.text(score + 1, i, f'{score:.1f} | {platform}',
                     va='center', color='white', fontweight='bold', fontsize=9)

        ax4.invert_yaxis()
        ax4.grid(True, alpha=0.3, axis='x')
        ax4.set_xlim(0, 105)

        plt.suptitle('E-LEARNING INTELLIGENCE DASHBOARD - CAREER ROI ANALYSIS',
                     fontsize=20, fontweight='bold', color='gold', y=0.98)

        plt.savefig(self.get_output_path('visualizations/elearning_intelligence_dashboard.png'), dpi=300, bbox_inches='tight',
                    facecolor='black', edgecolor='none')
        plt.close()

        logging.info("Intelligence visualizations created successfully!")

    def print_intelligence_summary(self, report: Dict):
        print("\n" + "*" * 90)
        print("ULTIMATE E-LEARNING & CERTIFICATION INTELLIGENCE - EXECUTIVE SUMMARY")
        print("*" * 90)

        summary = report['executive_summary']
        print(f"\nINTELLIGENCE METRICS:")
        print(f"   Total Courses Analyzed: {summary['total_courses_analyzed']:,}")
        print(f"   Total Trends Analyzed: {summary['total_trends_analyzed']:,}")
        print(f"   High-ROI Courses (>75): {summary['high_roi_courses']}")
        print(f"   ULTRA High-ROI (>85): {summary['ultra_high_roi']}")
        print(f"   Success Rate: {summary['success_rate_percentage']}%")
        print(f"   Average ROI Score: {summary['average_roi_score']}/100")

        if report['action_items'].get('immediate_learning_opportunities'):
            print(f"\nTOP 5 IMMEDIATE LEARNING OPPORTUNITIES:")
            for i, opp in enumerate(report['action_items']['immediate_learning_opportunities'][:5], 1):
                print(f"   {i}. {opp['course']}")
                print(f"      Platform: {opp['platform']} | ROI Score: {opp['roi_score']}/100")
                print(f"      Category: {opp['skill_category']} | Priority: {opp['priority']}")
                if opp.get('estimated_salary_boost') != 'Varies':
                    print(f"      Estimated Salary Boost: ${opp['estimated_salary_boost']:,}")

        if report.get('market_intelligence', {}).get('high_demand_skills'):
            print(f"\nHIGH-DEMAND SKILLS (Best Career ROI):")
            for skill in report['market_intelligence']['high_demand_skills'][:5]:
                print(f"   • {skill['skill'].replace('-', ' ').title()}")
                print(f"     ROI Score: {skill['avg_roi_score']}/100 | Salary Boost: ${skill['estimated_salary_boost']:,} | Demand: {skill['demand_level']}")

        print(f"\nINTELLIGENCE OUTPUTS:")
        print(f"   Database: elearning_intelligence.db")
        print(f"   Intelligence Dashboard: visualizations/elearning_intelligence_dashboard.png")
        print(f"   Comprehensive Report: reports/elearning_intelligence_*.json")
        print(f"   Screenshots: elearning_outputs/screenshots/")

        print("\n" + "*" * 90)
        print("INVEST IN YOUR FUTURE - MASTER HIGH-ROI SKILLS!")
        print("*" * 90)

    def export_all_data(self):
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')

        if self.course_data:
            df_courses = pd.DataFrame(self.course_data)
            df_courses.to_csv(self.get_output_path(f'elearning_outputs/courses_{timestamp}.csv'), index=False)
            df_courses.to_json(self.get_output_path(f'elearning_outputs/courses_{timestamp}.json'), orient='records', indent=2)
            logging.info(f"Exported {len(self.course_data)} courses to CSV and JSON")

        if self.skill_trends:
            df_trends = pd.DataFrame(self.skill_trends)
            df_trends.to_csv(self.get_output_path(f'elearning_outputs/skill_trends_{timestamp}.csv'), index=False)
            df_trends.to_json(self.get_output_path(f'elearning_outputs/skill_trends_{timestamp}.json'), orient='records', indent=2)
            logging.info(f"Exported {len(self.skill_trends)} skill trends to CSV and JSON")


def main():
    print("=" * 90)
    print("ULTIMATE E-LEARNING & CERTIFICATION INTELLIGENCE SCRAPER")
    print("Focus: High-ROI Courses | Trending Skills | Career Advancement")
    print("Features: Stealth Mode | Multi-Platform Analysis | Career Intelligence")
    print("=" * 90)

    scraper = ELearningIntelligenceScraper()

    skill_queries = [
        "python programming",
        "javascript web development",
        "react frontend",
        "machine learning",
        "data science",
        "artificial intelligence",
        "cloud computing aws",
        "docker kubernetes",
        "cybersecurity",
        "data analysis",
        "sql database",
        "tableau visualization",
        "power bi",
        "excel advanced",
        "project management pmp",
        "agile scrum",
        "digital marketing",
        "product management",
        "ux ui design",
        "graphic design",
        "blockchain",
        "prompt engineering",
        "chatgpt ai"
    ]

    reddit_communities = [
        "learnprogramming",
        "cscareerquestions",
        "datascience",
        "MachineLearning",
        "webdev",
        "ITCareerQuestions",
        "Python",
        "javascript"
    ]

    youtube_topics = [
        "python",
        "javascript",
        "machine learning",
        "data science",
        "web development",
        "cybersecurity"
    ]

    try:
        print("\nPhase 1: Udemy Course Intelligence Gathering")
        print("-" * 70)
        scraper.scrape_udemy_enhanced(skill_queries[:5], max_pages=2)

        print("\nPhase 2: Coursera Professional Certificate Analysis")
        print("-" * 70)
        scraper.scrape_coursera_enhanced(skill_queries[5:10], max_pages=2)

        print("\nPhase 3: freeCodeCamp Tech Trend Analysis")
        print("-" * 70)
        scraper.scrape_freecodecamp_trends()

        print("\nPhase 4: YouTube Learning Content Analysis")
        print("-" * 70)
        scraper.scrape_youtube_learning_trends(youtube_topics[:4])

        print("\nPhase 5: Reddit Community Learning Trends")
        print("-" * 70)
        scraper.scrape_reddit_learning_trends(reddit_communities[:6])

        print("\nPhase 6: Ultimate Intelligence Report Generation")
        print("-" * 70)
        ultimate_report = scraper.generate_ultimate_intelligence_report()

        print("\nPhase 7: Intelligence Visualization Dashboard")
        print("-" * 70)
        scraper.create_intelligence_visualizations()

        print("\nPhase 8: Intelligence Data Export")
        print("-" * 70)
        scraper.export_all_data()

        print("\n" + "*" * 90)
        print("MISSION ACCOMPLISHED: E-LEARNING INTELLIGENCE DEPLOYMENT COMPLETE!")
        print("*" * 90)

        total_courses = len(scraper.course_data)
        total_trends = len(scraper.skill_trends)
        ultra_high_roi = len([c for c in scraper.course_data if c.get('roi_score', 0) > 85])
        high_roi = len([c for c in scraper.course_data if c.get('roi_score', 0) > 75])

        print(f"\nULTIMATE INTELLIGENCE RESULTS:")
        print(f"   Total Courses Analyzed: {total_courses:,}")
        print(f"   Total Skill Trends: {total_trends:,}")
        print(f"   ULTRA High-ROI Courses (>85): {ultra_high_roi}")
        print(f"   High-ROI Courses (>75): {high_roi}")
        print(f"   Success Rate: {scraper.success_rate:.1f}%")
        print(f"   Screenshots Captured: {scraper.screenshots_saved}")
        print(f"   Failed Requests: {len(scraper.failed_requests)}")

        print(f"\nSTRATEGIC NEXT ACTIONS:")
        print(f"   1. Review generated reports in /reports folder")
        print(f"   2. Check visualizations in /visualizations folder")
        print(f"   3. Explore database: elearning_intelligence.db")
        print(f"   4. Review debug screenshots if any issues occurred")
        print(f"   5. Enroll in top {min(3, ultra_high_roi)} ultra-high ROI courses")

        print(f"\nCAREER ADVANCEMENT INSIGHT:")
        print(f"   Potential Salary Increase: $15,000 - $40,000+ based on chosen skills")
        print(f"   Time Investment: 3-6 months for certification completion")
        print(f"   ROI Timeline: 6-12 months to see career impact")

    except KeyboardInterrupt:
        print("\nIntelligence gathering interrupted by user")
        logging.info("Operation interrupted by user")
    except Exception as e:
        print(f"\nCritical error: {str(e)}")
        logging.error(f"Fatal error: {str(e)}")
        import traceback
        traceback.print_exc()
    finally:
        if scraper.course_data or scraper.skill_trends:
            scraper.export_all_data()
            print("\nEmergency data backup completed")


if __name__ == "__main__":
    main()
