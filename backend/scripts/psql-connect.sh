#!/bin/bash
# PostgreSQL CLI'ye direkt bağlanır

echo "🔌 PostgreSQL veritabanına bağlanılıyor..."
echo "📝 Faydalı komutlar:"
echo "   - Son 10 session: SELECT * FROM sessions ORDER BY created_at DESC LIMIT 10;"
echo "   - Toplam kayıt: SELECT COUNT(*) FROM sessions;"
echo "   - Hatalı sessionlar: SELECT * FROM sessions WHERE error_count > 0;"
echo "   - Çıkış: \\q"
echo ""

docker exec -it m3w-postgres psql -U m3w -d media3watch
