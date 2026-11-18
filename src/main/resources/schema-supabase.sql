-- ==============================================================================
-- CloudSaver - Script de Migração do Banco de Dados Supabase (PostgreSQL)
-- Criação de tabelas, triggers e Políticas de Segurança RLS (Row Level Security)
-- ==============================================================================

-- 1. Extensões úteis
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 2. Tabela de Perfis de Usuário (public.profiles)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ativação de Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Políticas de RLS para profiles
DROP POLICY IF EXISTS "Usuários podem visualizar o seu próprio perfil" ON public.profiles;
CREATE POLICY "Usuários podem visualizar o seu próprio perfil"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Usuários podem atualizar o seu próprio perfil" ON public.profiles;
CREATE POLICY "Usuários podem atualizar o seu próprio perfil"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Usuários podem inserir o seu próprio perfil" ON public.profiles;
CREATE POLICY "Usuários podem inserir o seu próprio perfil"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

-- Trigger para sincronizar novos usuários de auth.users para public.profiles
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, avatar_url)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================================================
-- 3. Tabela de Histórico de Análises / Scans (public.scans)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_recursos INT NOT NULL DEFAULT 0,
    total_paradas INT NOT NULL DEFAULT 0,
    custo_mensal NUMERIC(12, 4) NOT NULL DEFAULT 0,
    nome_arquivo VARCHAR(255) DEFAULT 'resources.json',
    recomendacao TEXT,
    resumo_json TEXT
);

-- Índices de consulta rápida
CREATE INDEX IF NOT EXISTS idx_scans_user_created_at ON public.scans (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scans_created_at ON public.scans (created_at DESC);

-- Ativação de Row Level Security (RLS)
ALTER TABLE public.scans ENABLE ROW LEVEL SECURITY;

-- Políticas de RLS para scans: isolamento total por usuário autenticado
DROP POLICY IF EXISTS "Usuários podem visualizar apenas os seus próprios scans" ON public.scans;
CREATE POLICY "Usuários podem visualizar apenas os seus próprios scans"
    ON public.scans FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Usuários podem inserir apenas os seus próprios scans" ON public.scans;
CREATE POLICY "Usuários podem inserir apenas os seus próprios scans"
    ON public.scans FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Usuários podem atualizar apenas os seus próprios scans" ON public.scans;
CREATE POLICY "Usuários podem atualizar apenas os seus próprios scans"
    ON public.scans FOR UPDATE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Usuários podem excluir apenas os seus próprios scans" ON public.scans;
CREATE POLICY "Usuários podem excluir apenas os seus próprios scans"
    ON public.scans FOR DELETE
    USING (auth.uid() = user_id);
