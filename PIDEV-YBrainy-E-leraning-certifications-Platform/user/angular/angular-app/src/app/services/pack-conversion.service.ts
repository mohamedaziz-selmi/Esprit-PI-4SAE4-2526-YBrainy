import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PackConversionSummary } from '../models/pack-conversion.model';

interface PackConversionAssetSummary {
  generated_at?: string | null;
  assumption?: string | null;
  scraper_inputs?: {
    courses_file?: string | null;
    skill_trends_file?: string | null;
  } | null;
  model?: {
    algorithm?: string | null;
    random_seed?: number | null;
    user_count?: number | null;
    training_rows?: number | null;
    test_rows?: number | null;
    positive_rate?: number | null;
    accuracy?: number | null;
    roc_auc?: number | null;
  } | null;
  top_conversion_packs?: Array<{
    pack_id?: number | null;
    title?: string | null;
    category_id?: number | null;
    category_name?: string | null;
    level?: string | null;
    primary_skill?: string | null;
    sale_price?: number | null;
    original_price?: number | null;
    discount_pct?: number | null;
    duration_hours?: number | null;
    market_demand_score?: number | null;
    market_avg_roi?: number | null;
    market_course_count?: number | null;
    avg_views?: number | null;
    avg_cart_adds?: number | null;
    observed_purchase_rate?: number | null;
    predicted_conversion_rate?: number | null;
    expected_buyers?: number | null;
    training_support?: number | null;
    expected_revenue?: number | null;
    conversion_rank?: number | null;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class PackConversionService {
  private readonly apiUrl = `${environment.apiUrl}/admin/packs/conversion`;
  private readonly fallbackSummaryUrl = 'assets/conversion-packs/pack_conversion_summary.json';

  constructor(private http: HttpClient) { }

  getSummary(limit = 10): Observable<PackConversionSummary> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<PackConversionSummary>(`${this.apiUrl}/summary`, { params }).pipe(
      catchError(() => this.getAssetSummary(limit))
    );
  }

  private getAssetSummary(limit: number): Observable<PackConversionSummary> {
    return this.http.get<PackConversionAssetSummary>(this.fallbackSummaryUrl).pipe(
      map((summary) => this.mapAssetSummary(summary, limit))
    );
  }

  private mapAssetSummary(summary: PackConversionAssetSummary, limit: number): PackConversionSummary {
    const topConversionPacks = (summary.top_conversion_packs ?? []).slice(0, limit).map((item) => ({
      packId: item.pack_id ?? null,
      title: item.title ?? null,
      categoryId: item.category_id ?? null,
      categoryName: item.category_name ?? null,
      level: item.level ?? null,
      primarySkill: item.primary_skill ?? null,
      salePrice: item.sale_price ?? null,
      originalPrice: item.original_price ?? null,
      discountPct: item.discount_pct ?? null,
      durationHours: item.duration_hours ?? null,
      marketDemandScore: item.market_demand_score ?? null,
      marketAvgRoi: item.market_avg_roi ?? null,
      marketCourseCount: item.market_course_count ?? null,
      avgViews: item.avg_views ?? null,
      avgCartAdds: item.avg_cart_adds ?? null,
      observedPurchaseRate: item.observed_purchase_rate ?? null,
      predictedConversionRate: item.predicted_conversion_rate ?? null,
      expectedBuyers: item.expected_buyers ?? null,
      trainingSupport: item.training_support ?? null,
      expectedRevenue: item.expected_revenue ?? null,
      conversionRank: item.conversion_rank ?? null
    }));

    return {
      sourceFile: this.fallbackSummaryUrl,
      generatedAt: summary.generated_at ?? new Date().toISOString(),
      assumption: summary.assumption ?? null,
      coursesFile: summary.scraper_inputs?.courses_file ?? null,
      skillTrendsFile: summary.scraper_inputs?.skill_trends_file ?? null,
      modelMetrics: summary.model ? {
        algorithm: summary.model.algorithm ?? null,
        randomSeed: summary.model.random_seed ?? null,
        userCount: summary.model.user_count ?? null,
        trainingRows: summary.model.training_rows ?? null,
        testRows: summary.model.test_rows ?? null,
        positiveRate: summary.model.positive_rate ?? null,
        accuracy: summary.model.accuracy ?? null,
        rocAuc: summary.model.roc_auc ?? null
      } : null,
      champion: topConversionPacks[0] ?? null,
      topConversionPacks
    };
  }
}
